# GigSyncHub

A self-hosted hub for a band's concerts. You keep your gigs in one place; it publishes them to the
listing platforms, keeps them in step when they change, reads your band calendar to find the gigs you
forgot to write down, and tells you every morning when a platform has quietly drifted out of line.

It began as one band's tool and grew into something several bands can share. It runs on one machine,
in Docker, and nothing leaves it except the requests to the platforms you connect yourself.

## What it does

- **One catalog, many platforms.** Your gigs live in a local PostgreSQL database — that is the source
  of truth. Publish a gig and it is created on every platform you've connected. Edit it, cancel it,
  reactivate it or delete it, and the same change follows everywhere it was published.
- **The platforms follow eventually, and you never wait for them.** Every change writes its platform
  work into an outbox in the same transaction, and a background worker drains it. The page is instant;
  a platform being slow or down is the worker's problem, not yours. Each gig shows its live state per
  platform — queued, running, retrying, failed, published — and updates itself as it moves.
- **Your band calendar, read as gigs.** Point it at your calendar's private iCal address and it sorts
  each event into *gig* / *unsure* / *not a gig* using weighted rules you can edit, pre-fills the gig
  form from the event (show time out of the notes, venue and town out of a map-style address), and
  tells you what changed since last time. Nothing is ever imported without you clicking. Private
  notes — fees, phone numbers — stay in your database and are never published anywhere.
- **A nightly check that catches drift.** Platforms change by hand and guess things on their own (a
  same-named town 70 km away is a favourite). Every night it reads each platform back and compares it
  with your catalog: events gone missing, a changed day, time, venue, town or country, a place
  geocoded somewhere wrong. You get a list with two buttons: re-sync, or forget the link.
- **Import what's already there.** First run against a platform you've been using for years: it reads
  your existing events, upcoming and past, matches them against your catalog, asks you about the
  ambiguous ones, and links the rest so they're managed from here from now on. It never deletes
  anything.
- **Several bands, one server.** Users, bands and memberships with owner/manager roles; a band's
  everything is its own, including its platform logins and its calendar. Invite by link, export a
  band as one file, delete an account. Each band's secrets are sealed with AES-256-GCM, and a
  ciphertext copied into another band's row doesn't open.
- **A phone-first page.** React, installable as a PWA, English and Slovak, with an Inbox that collects
  everything actually needing a human: failed sync work, platforms held back, calendar events to look
  at, the night's differences.

## How it hangs together

```
     the page (React PWA)                     your band calendar (iCal, read-only)
            │                                              │
            ▼                                              ▼
   ┌──────────────────────────────────────────────────────────────┐
   │  the catalog — PostgreSQL, the source of truth               │
   │  gigs · publications · the sync outbox · the calendar's copy │
   └──────────────────────────────────────────────────────────────┘
            │ every change also writes its platform work, same transaction
            ▼
     sync worker ──▶ Bandzone        (create / edit / cancel / remove)
                 ──▶ Bandsintown     (bulk upload / edit / remove)
                 ──▶ …a new platform is a new module
```

Requests to a platform are paced like a person's — a short random wait between them, a longer one
between batches, one request at a time per account — because the platforms' terms are written about
people using their pages, and a burst is what gets an account flagged.

## Platforms

| | Read | Publish & keep in step | How a band connects |
|---|---|---|---|
| **Bandzone.cz** | public band page | yes | types its login and password once; only the long-lived session is kept |
| **Bandsintown** | artist portal | yes | signs in once in its own browser and pastes what that leaves; no password is stored |
| **Calendar (iCal)** | yes, read-only | — | the calendar's private address |
| **Place search** | Photon / OpenStreetMap | — | nothing to connect |

Both platform clients are **off by default** (`off` = a stub that does nothing). Nothing reaches a
platform until you switch it on and a band connects its own account.

## Before you connect a platform — please read

This app drives the platforms' own web and portal interfaces on behalf of a band, using that band's
own account and credentials. There is no official write API for either of them.

- **Each band connects its own account** and is the one in a relationship with that platform. Read
  that platform's terms of use and decide for yourself whether you're comfortable; they're written to
  be read.
- **The connectors are best-effort.** They work against interfaces that can change without notice,
  and a platform may restrict or end an account's access at any time, for any reason. Treat them as a
  convenience over a catalog that keeps working regardless, not as something to rely on.
- **Keep the pacing.** It's deliberate, it's the polite thing, and it's what keeps your account out of
  trouble. If you fork this, don't remove it, and don't put a rotating proxy in front of it — a
  long-lived session arriving from a different address every time looks like a stolen one.
- Nothing here is legal advice. If you intend to run this for anyone other than yourself, talk to a
  lawyer first.

## Running it locally

### You need

- **Docker** with Compose. The five containers' memory limits add up to about 4 GB
  (`GSH_*_MEM` in `.env` if your machine is smaller); idle use is a fraction of that.
- That's it for running it. **Java 25** is needed only to build outside Docker; Maven comes with the
  repo (`./mvnw`) and Node is fetched automatically into `web/frontend/node` on the first build.

### Quick start

```bash
cp .env.example .env        # this machine's settings; git-ignored
docker/make-secrets.sh      # once: fills ./secrets with random passwords and keys
docker compose up -d --build
```

Then open **https://localhost**. Caddy issues its own certificate for `localhost`, so your browser
will warn you once — accept it, or trust Caddy's root certificate.

> **Set `GSH_DOMAIN` in `.env` before the first start** if this won't be `localhost`. The sign-in's
> bootstrap (which addresses it will redirect to) is read **only once**, while its database is empty.
> Changing it later means editing the client in Rauthy's admin by hand, so it's much cheaper to get
> right now. Online you also need DNS pointing at the machine and ports 80 and 443 open — Caddy gets
> a real Let's Encrypt certificate by itself.

### First sign-in

Signing in goes through **Rauthy**, an OpenID Connect provider that runs beside the app and is served
at `https://localhost/auth/`. On its very first start it creates an admin and **prints that admin's
password once, into its log**:

```bash
docker compose logs rauthy | grep -i -A2 password
```

Save it somewhere safe — it is not printed again. The admin is `admin@localhost` unless you set
`GSH_ADMIN_EMAIL`.

Now, in Rauthy's admin at **https://localhost/auth/v1/admin**:

1. Rauthy asks admins for a passkey. Register one first at `https://localhost/auth/v1/account`
   (that page doesn't require MFA itself). On a machine with nothing to make a passkey with, put
   `GSH_ADMIN_MFA=false` in `.env` and `docker compose up -d rauthy` — **never on a public server**,
   it's the sign-in's own admin account.
2. Add yourself as a user and put yourself in the group **`gigsynchub`**. Only members of that group
   may sign in to the app at all.
3. Go back to https://localhost and sign in. Your user and your first band are created for you.

Set `GIGSYNCHUB_ACCOUNTS_FIRSTOWNEREMAIL` in `.env` beforehand if you want a specific address to own
the first band; otherwise whoever signs in first does.

### Connect a band

Everything a band needs is on its **Settings → This band** page:

- **Platforms** — the form for each is built from what that platform needs. Switch the client on first
  (`GIGSYNCHUB_BANDZONE_CLIENT=http` / `GIGSYNCHUB_BANDSINTOWN_CLIENT=http` in `.env`, then
  `docker compose up -d app`), then connect the account and press **Test sign-in**. A band whose
  sign-in doesn't work isn't saved, so a broken account can't be connected.
- **Calendar** — the private iCal address and the band's time zone.
- Then **Import** (More → Import) if the band already has events on a platform, so they're linked
  rather than published again.

## Development

Run the database in Docker and the app on your machine:

```bash
docker compose up -d db
SPRING_DATASOURCE_PASSWORD=$(cat secrets/db_password) ./mvnw -pl app -am spring-boot:run
```

It serves on **http://localhost:8080**, and must run with the repo root as its working directory
(`./data` is relative to it).

The page, with hot reload:

```bash
cd web/frontend && npm run dev          # http://localhost:5173, API proxied to :8080
```

Signing in works at `:5173` too while `GSH_DOMAIN=localhost` — the dev server passes the OAuth
addresses through, and the bootstrap registered `http://localhost:5173` as a valid redirect.

### Building and testing

```bash
./mvnw clean install                    # every module, the page included
./mvnw test                             # all tests — needs Docker (Testcontainers)
./mvnw -pl platforms/bandzone -am test  # one module and what it needs
cd web/frontend && npm test             # the page's tests only
```

The platforms are tested against stand-ins that answer the way their real sites do, so no network and
no browser are involved. Persistence tests run the real migrations against a real PostgreSQL in
Docker, which is why `./mvnw test` needs a daemon running.

### Watching it

The app always exposes its own metrics and always puts a trace id into every log line. Whether
anything collects them is a compose profile that's off by default:

```bash
docker compose --profile observability up -d     # Prometheus, Loki, Tempo, Alloy, Grafana (~900 MB)
```

Grafana then answers on **http://127.0.0.1:3000** (admin/admin unless you set `GSH_GRAFANA_PASSWORD`),
with the links between the three provisioned: a log line offers its trace, a trace offers its logs.
Set `GSH_OTLP_ENABLED=true` and restart the app so it actually sends traces to Tempo.

Without the stack, the log line is still the whole story:

```bash
docker compose logs app | grep <traceId>
docker compose exec app curl -s localhost:8081/actuator/metrics/gigsynchub.sync.tasks
```

## Everyday commands

```bash
docker compose up -d --build                     # start, rebuilding the app
docker compose stop                              # stop everything, keep the data
docker compose --profile observability stop       # ...including the watching stack
docker compose logs -f app                       # follow the app's log
docker compose up -d --build app                 # just the app, after a code change
```

Containers come back by themselves after a crash or a reboot (`restart: unless-stopped`), while an
explicit `stop` is remembered. On a workstation where this is one project among several, put
`GSH_RESTART=no` in `.env` and nothing runs between a `stop` and the next `up`.

**Backups** happen by themselves: `pg_dump` of the app's and the sign-in's databases on start and then
daily into `./data/backups`, the newest 14 of each kept. Restoring is `docker compose run --rm backup
/restore.sh`. Two things no database dump can hold — **`./secrets` (above all `data_keys`, without
which every band's sealed logins and calendar address are gone for good) and `.env`** — so back those
up separately. The whole drill is in [docs/disaster-recovery.md](docs/disaster-recovery.md).

## Ports

| | | |
|---|---|---|
| 80, 443 | Caddy | the way in; HTTP redirects to HTTPS |
| 8080 | the app | `127.0.0.1` only (`GSH_APP_BIND=0.0.0.0` opens it to the LAN) |
| 5432 | PostgreSQL | `127.0.0.1` only, for development |
| 8081 | actuator | inside the container only — never through the proxy |
| 3000 | Grafana | `127.0.0.1` only, with the observability profile |
| 5173 | the page's dev server | only when you run `npm run dev` |

## Configuration

Everything about this machine is in **`.env`** (git-ignored; copy `.env.example`, which documents every
setting): the address, memory and CPU per container, which platform clients are live, where outbound
traffic comes from, and the observability switches.

**Secrets are files** under `./secrets`, mode 600, handed to the containers as Docker secrets and never
as environment variables. `docker/make-secrets.sh` creates them and keeps any that already exist.

## Where to read more

- **[CLAUDE.md](CLAUDE.md)** — the full technical account: every module, the sync workflows, the
  schema and its migrations, the rules the queries are held to, the operating envelope.
- **[docs/context-map.md](docs/context-map.md)** — why the same gig is a different type in seven
  places on purpose, and where the boundaries deliberately don't follow the invariants.
- **[docs/disaster-recovery.md](docs/disaster-recovery.md)** — restoring from nothing.
- `docs/*-plan.md` — how the sync engine, the calendar and multi-band support were designed.

## Project layout

```
features/gig          the shared kernel: Gig and its value objects, THE schema (Flyway)
features/accounts     users, bands, memberships, a band's platform accounts and sealed secrets
features/places       town lookup (Photon/OpenStreetMap)
features/sync         the outbox, the workflow engine, the worker, breakers, pause
features/catalog      the gig catalog's use cases and writes
features/importing    reading a platform's existing events and planning the import
features/check        the nightly reconciliation
features/calendar     the iCal read, the rules, the review, drafting gigs from events
platforms/portal      what every platform client shares
platforms/bandzone    Bandzone.cz, over plain HTTP — no browser
platforms/bandsintown Bandsintown's artist portal, over plain HTTP — no browser
web/live              live updates as server-sent events
web/frontend          the page: React + TypeScript + Vite + Mantine
app                   wiring, security, the REST surface, the architecture tests
```

One Maven module per feature, each with its own domain, application and adapters. A feature is used
from outside only through its domain and its ports, and the architecture tests fail the build when
that stops being true — including the rule that no feature's source may so much as name a platform.

## Licence

**GNU Affero General Public License, version 3 or later** — see [LICENSE](LICENSE).

```
GigSyncHub — a self-hosted hub for a band's concerts
Copyright (C) 2026 Peter Drabik

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along with
this program. If not, see <https://www.gnu.org/licenses/>.
```

What that means in practice:

- **Running it for your own band, or for your own bands: do whatever you like.** Use it, change it,
  keep your changes to yourself. The licence asks nothing of you.
- **Handing a copy to someone else, changed or not:** pass this licence and the source on with it.
- **Running a changed version as a service other people use:** those people must be able to get your
  changed source. That's the Affero part (§13), and it's the whole reason this licence rather than the
  plain GPL — for server software the GPL has a gap you can drive a product through.
- **Want to build something closed on top of it?** Then you want different terms, and I can grant
  them — I hold the copyright, so ask me. That conversation is possible precisely because the default
  here isn't permissive.

The source of this program is at **https://github.com/drabikp/gig-sync-hub**.
