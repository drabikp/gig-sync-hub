# Renaming bzscraper → gigsynchub: the cutover (2026-10-12)

The source rename is done and committed. Four things are **not** in the source: they live in the
running stack, and this is how they follow. Nothing here is reversible by a rebuild, so read it
before starting; the whole thing takes a few minutes and the app is down for about one of them.

A dump taken before any of this is the way back:

```bash
docker compose -p bzscraper exec -T backup sh -c \
  'PGPASSWORD="$(cat /run/secrets/db_password)" pg_dump --format=custom --dbname=bzscraper \
   --file=/backups/bzscraper-before-rename.dump'
```

(`data/backups/bzscraper-before-v10-gig-identity.dump` is the older one, from before V10.)

## 1. The data volumes

`compose.yaml` now says `name: gigsynchub`, and a compose project owns its volumes by name. So
`bzscraper_db-data` is no longer the volume the stack mounts — without this step the app would come
up against an **empty database** and migrate it from nothing.

The data is moved by copying the volumes, NOT by dump and restore. A copy keeps the whole PostgreSQL
cluster: its roles (`bzscraper_app` among them) and the row-level security policies that name them.
A `pg_restore` into a fresh cluster would bring the tables and the Flyway history — which says V6 has
run — but no roles for the policies to point at, and no V6 to create them again.

```bash
docker compose -p bzscraper down                  # stop cleanly; volumes are kept

for v in db-data caddy-data caddy-config; do
    docker volume create "gigsynchub_$v"
    docker run --rm -v "bzscraper_$v":/from:ro -v "gigsynchub_$v":/to alpine \
        sh -c 'cd /from && cp -a . /to/'
done
```

`caddy-data` carries the certificates — on `localhost` that is Caddy's own root, the one this
machine's browser was taught to trust; leave it behind and the browser warns again.
`bzscraper_browser-profiles` is not copied: nothing has driven a browser here since 2026-10-10 and
`compose.yaml` no longer declares it.

The old volumes stay on disk, untouched, until you remove them yourself — that is the undo.

## 2. The database and its owner

`POSTGRES_DB` / `POSTGRES_USER` only do anything when a volume is *empty*, so the copied cluster is
still called `bzscraper` and still owned by the role `bzscraper`. The `db` healthcheck now asks
`pg_isready -U gigsynchub -d gigsynchub`, so it reports unhealthy until this is done — which is why
only `db` is started first (`app` waits for a healthy `db`).

```bash
docker compose up -d db                           # the new project; ignore the unhealthy mark

docker compose exec -T db psql -U bzscraper -d postgres \
  -c 'alter database bzscraper rename to gigsynchub'
docker compose exec -T db psql -U bzscraper -d postgres \
  -c 'alter role bzscraper rename to gigsynchub'

docker compose ps db                              # healthy now
```

The app's own login, `bzscraper_app`, is **not** renamed here: `V11__rename_app_role.sql` does it on
the next start, and `AppRoleLogin` gives the renamed role its password afterwards, as it always has.

## 3. Rauthy: the client and the group

Rauthy keeps its own database, bootstrapped once and never read again, so the app's client is still
`bzscraper` there and so is the group whose members may sign in. The app now sends `gigsynchub` for
both. **Nobody can sign in between this step and the end of it**, so do it with Rauthy's admin
already open and do not sign out of it until the app works.

At `https://localhost/auth/v1/admin`:

1. **Groups** → rename `bzscraper` to `gigsynchub`. The members come with it.
2. **Clients** → the client `bzscraper`: set its id to `gigsynchub`, and its
   `restrict_group_prefix` to `gigsynchub` as well. Its **secret does not change**, so
   `secrets/auth_client_secret` stays as it is.

If the admin will not let a client's id be edited (Rauthy 0.37 treats it as the primary key), there
are two ways on, and the second is less work:

- make a new client `gigsynchub` for `https://localhost` with the same redirect
  (`/login/oauth2/code/rauthy`) and post-logout addresses, then put its secret in
  `secrets/auth_client_secret`; or
- leave Rauthy alone and tell the app which client it has: `GIGSYNCHUB_AUTH_CLIENTID=bzscraper` in
  `.env`. Everything else is renamed; one value keeps the old name until Rauthy is rebuilt.

## 4. Start it

```bash
docker compose up -d --build
docker compose logs -f app
```

What to look for, in order: Flyway applying **V11** (`rename_app_role`), no further migrations, the
app answering its health check, then a sign-in from the page.

```bash
docker compose exec app curl -s localhost:8081/actuator/metrics/gigsynchub.sync.tasks
```

Then check a gig page and the Activity page — the catalog should hold the same 289 gigs, with their
publications, sync history and calendar links.

## What keeps the old name on purpose

- **The applied migrations V1–V10.** Flyway checksums every migration it has run, so changing one
  byte — a comment included — would stop the app against this database. V6 goes on creating
  `bzscraper_app` and V11 renames it. V1's and V3's comments still mention the old property names.
- **`.bz-creds`, `.bz-test-creds`, `.bit-test-creds`, and `BZ_LIVE` / `BZ_LOGIN` / `BZ_PASSWORD` /
  `BZ_SLUG` / `BZ_LINEUP_BAND` / `BZ_KEEP`, `BIT_*`.** That `BZ` is **Bandzone** and `BIT` is
  **Bandsintown** — the platforms, not this app. The app's own machine settings became `GSH_*`
  (`GSH_DOMAIN`, `GSH_UID`, …) and its properties `GIGSYNCHUB_*`.
- **The dumps already in `data/backups/`** and the old H2 file `data/bzscraper-gigs.mv.db`: they were
  written under that name and keep it. New dumps are `gigsynchub-<day>.dump`, so the 14-file
  rotation in `docker/backup.sh` now prunes a different set of names — the old ones simply stay.
- **Each device's remembered band.** The page's keys became `gigsynchub.band` and
  `gigsynchub.returnTo`, so every browser forgets which band it had open, once.
