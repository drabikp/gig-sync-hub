# Many bands, then a public service — the plan

Today the app is one band's tool: one catalog, one Bandzone and one Bandsintown account and one
calendar (secret files and `.env`), one sync pause, one platform check. The goal is an app where
anyone can sign up, run one or more bands, share a band with its other managers — and, later, a
public service. Four phases; each one ends with a working app and is merged on its own.

| Phase | What it gives | Size |
|---|---|---|
| 1. Bands and memberships | several bands in one app, several people per band, a band switcher; your band becomes band #1 | L — every feature, spec and page |
| 2. Each band's own accounts | each band connects its Bandzone, Bandsintown and calendar in Settings; secrets in the database, encrypted | M |
| 3. A sync for many bands | fair turns, breakers / pause / check / import per band (browsers per account, as first planned, are gone — see the note after phase 4) | M |
| 4. Public | sign-up, invitations, roles, audit log, export and delete, isolation in the database, operations | L |

## The model

```
User (a Rauthy login)  ──<  Membership  >──  Band
  id, rauthy sub             role: owner | manager       id, slug, name, status
  e-mail, display name       display name (optional,     its gigs, platform accounts,
  language, time format       e.g. a stage name)          calendar, rules, settings, sync work
```

- **A "persona" is a membership**: this login, in this band, with this role — one per (user, band).
  One login has as many as it runs bands; a band has as many as it has managers. No second login
  per band (separate passwords, MFA and recovery for nothing); a per-band face is the membership's
  optional display name.
- **Rauthy is identity only**: sign-up, passwords, passkeys, MFA, recovery. Who may do what in which
  band is the app's (`membership`), not Rauthy's groups — the `gigsynchub` group gate goes away.
- **The person's** settings (language, 12/24-hour) are the user's; **everything else is the band's**.
- **One database schema, a `band_id` on every band's row** (not a schema or a database per band:
  heavier to run, nothing gained at this size); PostgreSQL row-level security as the second guard in
  phase 4.

## Decisions to confirm before phase 1

These are my proposals; each is cheap to change now and expensive later.

1. **Ids**: a band's id is a UUID (the API's `/api/bands/{bandId}/…`, never changes); its `slug`
   (unique, editable) is for the page's addresses (`/b/{slug}/gigs`) and the public list
   (`/gigs/{slug}`).
2. **Roles**: `owner` (everything, incl. members, accounts, deleting the band) and `manager` (gigs,
   sync, calendar, import, check). A read-only `viewer` later if asked for.
3. **The band's calendar rules** move from the file (`./data/calendar-profile.txt`) into the
   database, edited on the page (phase 2); the file is imported once.
4. **The key for the band secrets** is a Docker secret file (`secrets/data_keys`, like Rauthy's), not a
   cloud KMS — enough for one server, replaceable later behind the same port.
5. **Row-level security** comes in phase 4 (before going public), the app's own checks and the
   isolation tests from phase 1 on.
6. **Platform automation in public** (see *Risks*): opt-in per band, at the band's own risk, with the
   CSV export and "do it there by hand" always available — unless Bandsintown offers an API / partner
   access first.

---

## Phase 1 — bands and memberships

> **Status (2026-10-09): implemented.** As planned, with these choices made on the way: band #1's
> slug and name come from `gigsynchub.accounts.first-band-slug` / `first-band-name` (default: the
> Bandzone slug and the Bandsintown artist) at start instead of Flyway placeholders, its owner from
> `gigsynchub.accounts.first-owner-email`; `/api/auth/me` answers the user (`username`, `name`) and
> the page asks `/api/bands` for their bands; the module order puts `accounts` beside `places`
> (`gig ← accounts`; the check and Bandzone's public list use its `BandDirectory`); the platform
> clients refuse other bands with NEEDS_USER until phase 2; the Rauthy group still decides who may
> sign in at all (phase 4 opens it).

**Goal**: everything the app knows belongs to a band; a signed-in user sees and changes only the
bands they are a member of; your current data is band #1, you its owner. Platform accounts and the
calendar still come from the secret files — as band #1's (phase 2 moves them).

### A new feature module: `features/accounts`

Between the kernel and the features (`gig ← accounts ← places ← sync ← …`; it needs only the kernel):
- **domain**: `User`, `Band`, `Membership`, `Role`; rules: a band has at least one owner, a slug is
  unique and URL-safe, a user has one membership per band.
- **application**: `CurrentUserUseCase` (find or create the user on sign-in from the Rauthy `sub` —
  just-in-time), `BandsUseCase` (my bands, create a band → I'm its owner, rename, change slug),
  `MembersUseCase` (list; adding comes with invitations in phase 4), and the port every other module
  uses: `BandAccess` (`isMember(userId, bandId)`, `role(userId, bandId)`).
- **adapters**: JPA (`app_user`, `band`, `membership`), REST (`accounts.yaml`: `/api/me`,
  `/api/bands`, `/api/bands/{bandId}`, `/api/bands/{bandId}/members`).

### The kernel: `BandId` everywhere

- `BandId` (a UUID value) in `gig.domain`. **Every port and use case that touches a band's data takes
  it explicitly** — `GigRepository.findAll(BandId)`, `GigWrites.add(BandId, Gig)`,
  `QueueSyncWork.update(BandId, Gig)`, … — no thread-local "current band": the engine and the nightly
  jobs work for many bands in one thread, and explicit is testable and greppable.
- `GigId` stays a gig's identity *within* its band; records refer to a gig as `(band_id, gig_id)`.
  (It was the day and the venue when this was written; since V10 it is the gig's own id, and the day
  and venue are `GigMatch` / `gig.match_key`. The `(band_id, …)` keying is unchanged.)
- `LiveUpdates.changed(BandId, Topic)`; the stream is per band (`/api/bands/{bandId}/events`).
- New `ArchitectureTest` rule: every JPA repository method on a band's table takes the band (no
  unscoped `findAll` outside the workers' "due across bands" queries, which are named as such).

### The database — migration V2

```sql
create table app_user   (id uuid pk, rauthy_sub varchar unique not null, email, display_name,
                         language, time_format, created_at);
create table band       (id uuid pk, slug varchar unique not null, name not null, status, created_at);
create table membership (user_id → app_user, band_id → band, role not null, display_name,
                         created_at, primary key (user_id, band_id));
```

Every band's table gets `band_id uuid not null references band` and its keys include it:

| Table | New key / indexes |
|---|---|
| `gig` | pk `(band_id, id)` |
| `published_gig` | pk `(band_id, platform, gig_id)` |
| `sync_task` | `band_id`; indexes `(band_id, gig_id)`, `(status, next_attempt_at)` stays (the worker looks across bands) |
| `sync_log` | through its task |
| `calendar_event`, `calendar_decision`, `calendar_link` | pk `(band_id, event_uid)` |
| `calendar_rule` | `band_id` null for the shipped presets (shared), set for a band's own rules |
| `app_setting` | stays for the app's own settings; a band's (the sync pause) go to `band_setting (band_id, name)` |

The migration creates **band #1** (a fixed UUID; name and slug from the Flyway placeholders
`first-band-name` / `first-band-slug`, set from `.env` — `GIGSYNCHUB_BANDZONE_BANDSLUG` by default)
and fills `band_id` of every existing row with it — one transaction, checked by counts in a test
(288 gigs, 427 publications, 438 calendar events stay 288 / 427 / 438).

**Owner of band #1**: `gigsynchub.bootstrap.owner-email` (`.env`); the first sign-in with that e-mail
becomes band #1's owner. Without it, the first user to sign in claims the band that has no owner
(fine locally, a setting online).

### The API

- Every feature's paths move under the band: `/api/bands/{bandId}/gigs`, `…/sync/…`, `…/calendar/…`,
  `…/import/…`, `…/check/…`, `…/exports/{platform}`, `…/events`. The specs change first (a shared
  `BandIdParam` in gig.yaml); the generated interfaces get `bandId`; controllers pass it on — still
  no logic in them.
- **Access**: one `BandAccessInterceptor` (app/web) for `/api/bands/{bandId}/**`: the signed-in user
  must be a member, else **404** (a band you aren't in doesn't exist for you — no probing of ids);
  owner-only actions are checked in the use cases (`AccessDeniedException` → 403 `ownerOnly`).
- `/api/auth/me` answers the user with their bands (id, slug, name, role); the group `member` flag
  goes.
- The public `/gigs/{band_slug}` finds the band by its slug.
- `ErrorCodesTest`, `ApiSecurityTest` extend to the new codes and rules.

### The page

- Routes under the band: `/b/{slug}/…` (gigs, inbox, calendar, activity, import, check, settings);
  `/` sends to the last band used (remembered on the device) or, without a band, to **"Create your
  band"** (name, slug → owner).
- A **band switcher** at the top of the menu (desktop) / in More (phone): name, role; "New band".
- Every hook takes the band (`useGigs(bandId)` …, query keys `['band', bandId, …]`); the live stream
  per band; switching bands drops the other band's cached data.
- **Settings** splits into *You* (language, time format, sign out) and *This band* (name, slug,
  members — read-only list until phase 4, platforms as today).

### Tests

- **Isolation suite** (app): two bands with the same-looking gigs, two users; for **every** API path in
  the specs (read from the OpenAPI files, so a new path can't be forgotten), user A on band B's id →
  404, and A's writes never touch B's rows. Plus: the same gig (date + venue) in two bands is two gigs.
- Each feature's tests run with a band (the fakes key everything by band).
- The migration test (counts, band #1, keys).

**Done when**: your data is band #1 with you as owner; a second user can create a band and sees
nothing of band #1; everything works as now within a band; the isolation suite passes.

---

## Phase 2 — each band's own platform accounts and calendar

> **Status (2026-10-10): implemented.** As planned, with these choices made on the way: the account's
> fields are part of the platform's `PlatformTraits` (`AccountField`: text, secret, yes/no — the page's
> form and the server's check come from them); `SecretBox` (AES-256-GCM) lives in `features/accounts`
> (the calendar uses it through its port); the keys are `secrets/data_keys` (`id:base64`, the first
> active; on start everything an older key sealed is sealed again); a platform's sign-in test runs while
> the page waits (up to a minute); the browser profile is per band and login (a saved login is never
> reused by another band). Left for later: choosing the presets per band (they are everyone's), the
> thresholds per band, switching off a shipped rule per band (a band adds a counter-rule instead), and
> deleting the old secret files (`make-secrets.sh` still writes them for the one-time move; empty is fine).

**Goal**: a band connects its own Bandzone and Bandsintown accounts and its calendar on the page; no
band data in secret files or `.env` any more.

### The store

```sql
create table band_platform_account (band_id, platform, settings jsonb,   -- not secret: slug, artist name/id, notify followers
                                     secrets bytea, key_id,               -- encrypted: login, password, authenticator secret
                                     status, checked_at, primary key (band_id, platform));
create table band_calendar        (band_id pk, ical_url bytea, key_id, zone, presets, thresholds);
```

- **Encryption** (`features/accounts`, port `SecretBox`): AES-256-GCM, the master keys from the Docker
  secret `secrets/data_keys` (`id/base64`, several for rotation, one active), the row's
  `(band_id, platform)` as associated data (a ciphertext can't be moved to another row). Secrets
  are decrypted only in the moment a platform session starts, never logged (the `toString`s), never
  sent to the page (it gets "connected as j***@…").
- **Key rotation**: a new active key, a job re-encrypts every row, the old key removed after.

### Platform-agnostic: the platform describes its account

- A platform module declares its account's fields — `PlatformAccountForm` next to its
  `PlatformTraits`: e.g. Bandzone `login`, `password` (secret), `band slug`; Bandsintown `login`,
  `password`, `authenticator secret` (secret), `artist name`, `artist id`, `notify followers`. The
  page renders Settings → Platforms from that — no feature or page names a platform.
- **Connect / test**: the platform's `AccountCheck` signs in once (Selenium) and says ok / wrong
  password / needs the authenticator / several artists.
- Platform modules stop reading accounts from their properties: the steps, importers and exporters
  get the band's `PlatformAccount` (through a port `PlatformAccounts.of(bandId, platform)`). What
  stays in properties is the app's own: base URLs, pacing, Chromium paths, a kill switch per platform.
- The browser profile is already per account (its hash) — it becomes per (band, platform, login).

### The calendar

- The iCal address per band (encrypted — it's a secret link), its zone and presets; the band's own
  rules edited on the page (Calendar → Rules: add / weight / switch off), the old file imported once.

### Moving band #1

On start, if band #1 has no accounts and the old secret files / `.env` values are there, they are
imported once (encrypted) and the log says so; then the files can be deleted (`make-secrets.sh` stops
making them).

**Done when**: a new band connects Bandzone, Bandsintown and a calendar on the page and everything works for
it; band #1 runs from the database; no band secret in `./secrets` but the master keys.

---

## Phase 3 — a sync for many bands

> **Status (2026-10-10): implemented.** Lanes (a band's work on a platform) run on several workers
> (`gigsynchub.sync.worker.threads`, 2), the bands in turn (`FairTurns`); breakers per lane and per platform
> above them; the browser pool (`gigsynchub.browser.max`), `max-sessions` per platform, one session per
> account, the idlest warm browser closed to make room; each band's check result in `band_check`; metrics
> per band on the operator's management port (8081, this machine only). Load tests with fakes:
> `ManyBandsSyncTest` (ten bands, a broken account, three workers), `BrowserPoolTest`. Left for later: the
> operator's global pause (stopping the worker, `gigsynchub.sync.worker.enabled=false`, does it for now),
> spreading the nightly checks over the night, a second sync lane of one band never waiting on a busy
> platform session (it is postponed a minute instead).

**Goal**: many bands' work runs on one server, fairly, without one band's trouble holding the others.

- **Browsers per account, with a limit**: `PlatformBrowser` per (band, platform) account — one session
  per account at a time, never two bands in one browser — and a pool limit for the server
  (`gigsynchub.browser.max`, ~2–4 on a small server: a Chromium is 300–500 MB). Warm browsers per
  account, the idle one closed first when the pool is full.
- **Fair turns**: the dispatcher takes due work round-robin across bands (not oldest-first across
  everything), batches per (band, platform, step); a band with a hundred tasks doesn't starve others.
- **Breakers per account** (a wrong password holds back only that band), and one per platform above
  them (most accounts failing at once = the platform is down → hold all).
- **Pause per band** (`band_setting`) and an operator's global pause.
- **The platform check per band**: results stored (`band_check`, not in memory — survive restarts),
  the nightly runs spread over the night, one band at a time.
- **Import per band** (one session per band), **calendar reads per band**, **live updates per band**.
- **Human pacing per account**, and a limit of parallel sessions per platform from this server's
  address (many accounts from one IP look like a bot farm).
- Metrics per band: tasks waiting / failed, sessions, browser pool use.

**Done when**: ten test bands with work queued at once all finish, none waits on another's broken
account, the pool limit holds (a load test with the platforms' page fixtures).

---

## Phase 4 — public

**Accounts and access**
- Rauthy: open registration, e-mail (SMTP) for verification and recovery, passkeys / MFA offered
  (required for owners, perhaps); the app's client no longer restricted to a group.
- **Invitations**: an owner invites by e-mail (a link with a one-time token, valid 7 days); accepting
  needs signing in with that e-mail; roles owner / manager; leave a band, hand over ownership,
  remove a member, delete a band (owners).
- **Delete my account**: memberships go (a band whose only owner leaves must get another owner or be
  deleted), the app user goes, the Rauthy user through its admin API (an API key).

**Data protection**
- **Row-level security** on every band table: the app connects as a role without bypass; each
  transaction sets `app.band_id`; the workers' cross-band queries run as a separate role that only
  reads the due-work index. The isolation suite runs against it.
- **Audit log** (`audit_event`: band, user, action, what, when) written by the use cases; owners see
  their band's.
- **Export** a band (gigs, publications, calendar decisions — JSON / CSV) and **delete** it (rows,
  secrets, browser profiles, backups age out); GDPR records: what is kept, why, how long. Calendar
  notes hold third parties' phone numbers and fees — kept only in the database, never published,
  deletable with the band.
- Terms of service and a privacy policy; who the operator is.

**Operations**
- Rate limits (Caddy and per user / band in the app), quotas (gigs, platform runs per day, bands per
  user), sign-up abuse protection.
- Logs with band and user ids (MDC), metrics and alerts (Micrometer → Prometheus/Grafana or a hosted
  one), error tracking.
- Backups as today plus a tested restore, encrypted at rest (an encrypted volume), offsite copies.
- Security review (`/cso`) and dependency scanning before opening; then sign-up opens.
- Later, if needed: workers as their own containers (the sync's browsers on their own machine), more
  app instances (sessions in the database, the worker leader-elected).

**Done when**: a stranger can sign up, create a band, invite a co-manager, connect platforms and
publish — and can't see anything else; the security review passes.

**Status (2026-10-10)** — done, and live on the local stack: invitations (a link, no e-mail yet), roles,
leave / remove / hand over, delete a band (its slug typed) and one's own account (the Rauthy login
stays: no admin API key yet); row-level security (V6) — the app's own login `gigsynchub_app`, fail-closed
(nothing opened = no band's rows), the cross-band reads say so; the audit log (`AuditInterceptor`:
every change through a band's API, rather than written by each use case); export (one JSON file, no
secret); rate limits (per user / address, 429), the bands-owned quota, live-stream caps; the public
list opt-in (V7); a security review and its fixes (the band's addresses fetched only from the public
internet, redirects checked, 10 MB cap; gig links http(s) only; failed logins without the browser's
message; tokens out of Caddy's log; unverified e-mails ignored).
Still the operator's to decide or do before opening sign-up: SMTP for Rauthy (verification, recovery,
invitation mails); open registration and drop the group restriction; ToS, privacy policy and who the
operator is; ask Bandsintown (and Bandzone) for API / partner access; a Rauthy API key (delete the login
with the account); Rauthy on its own database instance and its admin reachable only from a known address; an
egress proxy for the bands' addresses (DNS rebinding remains possible between the check and the
fetch); encrypted, offsite backups with `data_keys` kept apart; require `first-owner-email`;
MDC logs, alerts, dependency scanning.

---

## No browser on the server (2026-10-10)

Phases 1–3 were built around driving the platforms' pages in Chromium: a browser per account, a pool
limit, warm browsers, `max-sessions`. That is all gone. Bandzone's admin turned out to be plain
server-rendered forms, so the app sends what those forms send; Bandsintown's portal has an API its own
pages call, and the one step that genuinely needs a browser — its identity provider allows only the
browser's sign-in exchange — is done by the band in **its own** browser, pasting what that leaves into
Settings. So the server installs no browser, stores no platform password and no authenticator secret,
and nothing queues behind a scarce browser. What the phase-3 notes say about the pool, warm browsers
and sessions per account is history; the fair turns, the per-lane breakers and the per-band check,
import and pause all stand.

## Risks

- **The platforms' terms and bot detection.** The app sends what Bandzone's and Bandsintown's own
  pages send, on the band's own account. For one band that's a band automating its own account; as a
  service it's a third party acting for many bands from one server — and a client that is not a
  browser is easier to pick out, which is why requests are paced like a person's. It no longer holds
  any band's password or authenticator secret, only a session each band can revoke. Before opening:
  ask Bandsintown for API / partner access (and Bandzone), make automation opt-in per band with clear
  wording, keep the CSV export and the by-hand paths first-class, and expect to reimplement a client
  when a platform changes its pages.
- **Holding the bands' secrets** — the most valuable thing to steal. Encryption with keys outside the
  database, nothing in logs, the security review, an encrypted disk.
- **Phase 1 touches everything** — every feature, spec, page and test. Done module by module along
  the build order (kernel, accounts, places, sync, catalog, importing, check, calendar, platforms,
  app, page), each step building and passing, one branch.
- **Costs**: with no browser to run, a band's sync is a handful of HTTP requests, so what bounds how
  many bands one server carries is the worker threads and the platforms' own patience, not memory.
  The fair turns keep it predictable.
