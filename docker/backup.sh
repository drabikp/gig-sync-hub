#!/bin/sh
# The databases' backup (compose service "backup"): pg_dump in PostgreSQL's custom format of the
# app's database and the sign-in's (rauthy) on start and then every day into /backups, keeping
# the newest $KEEP of each, plus the cluster's roles (pg_dumpall --globals-only), which live in
# neither database and which a restore needs before either of them will load.
#
# Restoring is docker/restore.sh; docs/disaster-recovery.md is the whole drill, including the two
# things that are NOT here and cannot be: ./secrets (above all data_keys, without which every
# band's sealed logins and calendar address are lost for good) and .env. They are deliberately
# left out — an encrypted copy of the key beside the ciphertext it opens is not a backup, it is a
# longer password — and they are the operator's to copy somewhere else.
set -eu
# the dumps hold every band's data and their sealed secrets: nobody else on the host reads them
umask 077
KEEP="${KEEP:-14}"
PGPASSWORD="$(cat /run/secrets/db_password)"
export PGPASSWORD

# Dumps $1 into $2, through a .part so a failed or interrupted run never replaces yesterday's good
# dump with half of today's.
dump_to() {
    if $1 --file="$2.part"; then
        mv "$2.part" "$2"
        echo "backup: $2"
        return 0
    fi
    rm -f "$2.part"
    echo "backup: $2 failed" >&2
    return 1
}

while true; do
    day="$(date +%F)"
    failed=0
    for db in gigsynchub rauthy; do
        dump_to "pg_dump --format=custom --dbname=$db" "/backups/$db-$day.dump" || failed=1
        # ISO dates in the names sort by age; drop all but the newest $KEEP
        ls -1 "/backups/$db"-*.dump 2>/dev/null | sort | head -n "-$KEEP" | while read -r old; do rm -f "$old"; done
    done
    # The roles and their passwords: owned by the cluster, not by a database, so a dump of each
    # database restores into nothing (no gigsynchub_app to set role to, no rauthy to own its rows).
    dump_to "pg_dumpall --globals-only" "/backups/globals-$day.sql" || failed=1
    ls -1 /backups/globals-*.sql 2>/dev/null | sort | head -n "-$KEEP" | while read -r old; do rm -f "$old"; done

    if [ "$failed" = 1 ]; then
        echo "backup: next try in an hour" >&2
        sleep 3600
        continue
    fi
    # What a watchdog reads: the last time a WHOLE round came through. A backup nobody checks is a
    # backup nobody has, and the failure that matters is the silent one — the container running
    # happily while every dump fails.
    date -u +%FT%TZ > /backups/LAST_OK
    sleep 86400
done
