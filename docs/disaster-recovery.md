# If the server is gone

What it takes to have this app running again on a new machine, and — more usefully — what has to
be true BEFORE that day for it to be possible at all.

## The three things, and only one of them is the database

1. **The dumps** (`./data/backups`): the app's database and the sign-in's, plus the cluster's
   roles. Written daily by the compose service `backup` (`docker/backup.sh`), the newest 14 of
   each kept.
2. **`./secrets`** — above all **`data_keys`**. Every band's platform logins and its calendar
   address are kept SEALED in the database with that key. A dump without it restores rows that
   will never open again: the app starts, the bands' pages show no connections, and nothing says
   why. Nothing in this repository can back it up for you, and that is deliberate — an encrypted
   copy of the key beside the ciphertext it opens is not a backup, it is a longer password.
3. **`.env`** — this machine's own settings (`GSH_DOMAIN` above all). Cheap to write again from
   `.env.example`, but write down the domain: Rauthy's bootstrap is read only into an EMPTY
   database, so a restored Rauthy expects the domain it was set up with.

**Copy 2 and 3 off this machine, somewhere the dumps are not.** That is the whole of the
preparation, and it is the part nobody does.

## Is the backup alive?

`./data/backups/LAST_OK` holds the time the last whole round came through. The failure that
matters is not the loud one; it is the backup container running happily while every dump fails, so
check the file's age rather than the container's status:

```bash
cat data/backups/LAST_OK                  # a day old at most
ls -l data/backups                        # and the dumps themselves are not 0 bytes
```

## The drill

Try it before you need it — on a spare machine, with yesterday's dumps. A restore nobody has run
is a guess.

```bash
git clone <this repository> gigsynchub && cd gigsynchub
cp /somewhere-safe/.env .env              # the domain above all
cp -a /somewhere-safe/secrets ./secrets   # chmod 600 ./secrets/* is what make-secrets.sh leaves
chmod 700 secrets secrets/rauthy-bootstrap
mkdir -p data/backups && cp /somewhere-safe/*.dump /somewhere-safe/globals-*.sql data/backups/

docker compose up -d db                   # the database alone, empty
docker compose run --rm -v "$PWD/data/backups:/backups" \
  -v "$PWD/docker/restore.sh:/restore.sh:ro" backup /restore.sh     # DAY=2026-10-13 for a given day
docker compose up -d --build              # everything, against the restored data
```

`docker/restore.sh` refuses a database that already holds tables, puts the roles back first (both
databases' rows are owned by roles that live in the cluster, not in them), and says what is left to
do.

## What you get back, and what you do not

- **Back**: every band, its gigs, publications, sync work, calendar copy and verdicts, its members
  and audit log; every Rauthy account, group and passkey (a passkey is bound to `RP_ID`, so it
  works again only under the SAME domain).
- **Not back**: anything only in memory — which breaker is open, which import session was half
  decided, the live streams. All of it is rebuilt by the app as it runs: breakers close, the
  nightly check runs again, the page reconnects.
- **Not back, and worth knowing before you promise otherwise**: the platforms themselves. The app
  is the catalog; the events on Bandzone and Bandsintown are where they were. If the catalog went
  back a day, the nightly platform check will find the difference and the Inbox will say so.

## A new domain

Rauthy's bootstrap (`secrets/rauthy-bootstrap`) is read once, into an empty database, so it does
not help here. On a restored Rauthy with a new `GSH_DOMAIN`: sign in at `/auth/v1/admin` and
change the `gigsynchub` client's redirect and post-logout addresses, and `PUB_URL`/`RP_ID` follow
`.env`. Every existing passkey is bound to the old domain and will have to be registered again.
