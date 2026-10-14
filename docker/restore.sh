#!/bin/sh
# Restoring what docker/backup.sh wrote, into an EMPTY database cluster. Run it from the repo root
# with compose up for `db` only:
#
#   docker compose up -d db
#   docker compose run --rm -v "$PWD/data/backups:/backups" -e DAY=2026-10-13 backup /restore.sh
#
# DAY is which day's dumps to take (default: the newest there is). It refuses to touch a database
# that already holds anything, because the one thing worse than no restore is half of one on top of
# live data.
#
# What this does NOT restore, and what no dump can: ./secrets. Without `data_keys` every band's
# platform logins and calendar address are ciphertext for ever, and the app will start and show
# empty connections as though the bands had never set them up. Put ./secrets back BEFORE starting
# the app. See docs/disaster-recovery.md.
set -eu
umask 077
PGPASSWORD="$(cat /run/secrets/db_password)"
export PGPASSWORD

newest() {
    ls -1 "/backups/$1"-*."$2" 2>/dev/null | sort | tail -n 1
}

day="${DAY:-}"
if [ -z "$day" ]; then
    newest_dump="$(newest gigsynchub dump)"
    [ -n "$newest_dump" ] || { echo "restore: no dump in /backups" >&2; exit 1; }
    day="$(basename "$newest_dump" .dump | sed 's/^gigsynchub-//')"
fi
echo "restore: taking the dumps of $day"

globals="/backups/globals-$day.sql"
app="/backups/gigsynchub-$day.dump"
auth="/backups/rauthy-$day.dump"
for file in "$globals" "$app" "$auth"; do
    [ -f "$file" ] || { echo "restore: $file is missing" >&2; exit 1; }
done

# The roles first: both databases' rows are owned by roles that live in the cluster, not in them.
# Errors are shown and not fatal — a role that is already there (the compose db makes gigsynchub
# itself) is the normal case, not a problem.
echo "restore: roles"
psql -d postgres -f "$globals" || echo "restore: some roles were already there (that is fine)"

restore_into() {
    database="$1"
    file="$2"
    owner="$3"
    tables="$(psql -tAd "$database" -c \
        "select count(*) from pg_tables where schemaname not in ('pg_catalog','information_schema')" 2>/dev/null || echo missing)"
    if [ "$tables" = missing ]; then
        echo "restore: making the database $database"
        psql -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$database\" OWNER \"$owner\""
    elif [ "$tables" != 0 ]; then
        echo "restore: $database already holds $tables tables — refusing. Drop it first if you mean it." >&2
        exit 1
    fi
    echo "restore: $database from $file"
    pg_restore --dbname="$database" --no-owner --role="$owner" --exit-on-error "$file"
}

restore_into gigsynchub "$app" gigsynchub
restore_into rauthy "$auth" rauthy

echo "restore: done. Put ./secrets back before starting the app, then: docker compose up -d"
