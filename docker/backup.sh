#!/bin/sh
# The databases' backup (compose service "backup"): pg_dump in PostgreSQL's custom format of the
# app's database and the sign-in's (rauthy) on start and then every day into /backups, keeping
# the newest $KEEP of each. Restore into an empty database:
#   pg_restore -d bzscraper --no-owner bzscraper-<day>.dump   (rauthy: -d rauthy --role=rauthy)
set -eu
KEEP="${KEEP:-14}"
PGPASSWORD="$(cat /run/secrets/db_password)"
export PGPASSWORD
while true; do
    day="$(date +%F)"
    failed=0
    for db in bzscraper rauthy; do
        if pg_dump --format=custom --dbname="$db" --file="/backups/$db-$day.dump.part"; then
            mv "/backups/$db-$day.dump.part" "/backups/$db-$day.dump"
            echo "backup: /backups/$db-$day.dump"
            # ISO dates in the names sort by age; drop all but the newest $KEEP
            ls -1 "/backups/$db"-*.dump | sort | head -n "-$KEEP" | while read -r old; do rm -f "$old"; done
        else
            rm -f "/backups/$db-$day.dump.part"
            echo "backup: pg_dump of $db failed" >&2
            failed=1
        fi
    done
    if [ "$failed" = 1 ]; then
        echo "backup: next try in an hour" >&2
        sleep 3600
        continue
    fi
    sleep 86400
done
