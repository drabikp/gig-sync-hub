#!/usr/bin/env bash
# Builds ./secrets (git-ignored, readable by the owner only) for compose.yaml from the
# git-ignored login files of a local checkout. Prints only the names, never a value.
# Keeps a secret that already exists; delete its file to make it again.
#   .bz-creds          line 1 Bandzone login, line 2 password
#   .bit-test-creds    line 1 Bandsintown login, line 2 password, line 3 authenticator secret
#   calendar           line 1 the band calendar's private iCal address
# These are band #1's: the app keeps them once in its database (encrypted) and each band connects its own
# on the page after that. Missing sources give empty files (nothing is kept).
# The bands' secrets are sealed with data_keys (random; never lose it — without it they can't be read):
# to rotate, put a new key on the first line (k<date>:<base64 of 32 bytes>) and keep the old one below it
# until the app has started once (it seals everything again with the new key).
# The sign-in (Rauthy) gets random secrets: its secrets file, its database password, the app's
# client secret, and its bootstrap files (the group bzscraper and the app's client, for
# https://$BZ_DOMAIN from .env) — read by Rauthy only while its database is empty.
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
mkdir -p secrets
chmod 700 secrets

# secret name, source file, line ("" = the whole file)
put() {
    local name="$1" source="$2" line="$3" target="secrets/$1"
    if [[ -s "$target" ]]; then
        echo "kept    $target"
        return
    fi
    if [[ -f "$source" ]]; then
        if [[ -n "$line" ]]; then
            sed -n "${line}p" "$source" | tr -d '\r\n' > "$target"
        else
            tr -d '\r\n' < "$source" > "$target"
        fi
        echo "made    $target (from $source)"
    else
        : > "$target"
        echo "empty   $target ($source not found)"
    fi
}

if [[ -s secrets/db_password ]]; then
    echo "kept    secrets/db_password"
else
    head -c 32 /dev/urandom | base64 | tr -d '/+=\n' > secrets/db_password
    echo "made    secrets/db_password (random)"
fi
if [[ -s secrets/db_app_password ]]; then
    echo "kept    secrets/db_app_password"
else
    head -c 32 /dev/urandom | base64 | tr -d '/+=\n' > secrets/db_app_password
    echo "made    secrets/db_app_password (random: the app's own database login, bzscraper_app)"
fi
if [[ -s secrets/data_keys ]]; then
    echo "kept    secrets/data_keys"
else
    echo "k$(date +%Y%m%d):$(head -c 32 /dev/urandom | base64 | tr -d '\n')" > secrets/data_keys
    echo "made    secrets/data_keys (random)"
fi
put bandzone_login .bz-creds 1
put bandzone_password .bz-creds 2
put bandsintown_login .bit-test-creds 1
put bandsintown_password .bit-test-creds 2
put bandsintown_totp .bit-test-creds 3
put calendar_url calendar 1

# --- the sign-in (Rauthy) ---
random_text() {
    head -c 96 /dev/urandom | base64 | tr -d '/+=\n' | head -c "$1"
}
if [[ -s secrets/rauthy.toml && -s secrets/rauthy_db_password ]]; then
    echo "kept    secrets/rauthy.toml, secrets/rauthy_db_password"
else
    random_text 40 > secrets/rauthy_db_password
    cat > secrets/rauthy.toml <<TOML
# Rauthy's secrets (docker/rauthy/config.toml names each as '\$SECRETS')
[encryption]
keys = ['k$(date +%Y%m%d)/$(head -c 32 /dev/urandom | base64)']
key_active = 'k$(date +%Y%m%d)'

[cluster]
secret_raft = '$(random_text 48)'
secret_api = '$(random_text 48)'

[database]
pg_password = '$(cat secrets/rauthy_db_password)'
TOML
    echo "made    secrets/rauthy.toml, secrets/rauthy_db_password (random)"
fi
if [[ -s secrets/auth_client_secret ]]; then
    echo "kept    secrets/auth_client_secret"
else
    random_text 64 > secrets/auth_client_secret
    echo "made    secrets/auth_client_secret (random)"
fi
domain="$(sed -n 's/^BZ_DOMAIN=\([^ #]*\).*/\1/p' .env 2>/dev/null | tail -1)"
domain="${domain:-localhost}"
redirects="\"https://$domain/login/oauth2/code/rauthy\""
after_logout="\"https://$domain/\""
if [[ "$domain" == localhost ]]; then
    # the page's dev server (npm run dev) on this machine
    redirects="$redirects, \"http://localhost:5173/login/oauth2/code/rauthy\""
    after_logout="$after_logout, \"http://localhost:5173/\""
fi
mkdir -p secrets/rauthy-bootstrap
chmod 700 secrets/rauthy-bootstrap
cat > secrets/rauthy-bootstrap/groups.json <<JSON
[ { "name": "bzscraper" } ]
JSON
cat > secrets/rauthy-bootstrap/clients.json <<JSON
[
  {
    "id": "bzscraper",
    "name": "bzscraper",
    "secret": { "Plain": "$(cat secrets/auth_client_secret)" },
    "redirect_uris": [$redirects],
    "post_logout_redirect_uris": [$after_logout],
    "allowed_origins": ["https://$domain"],
    "enabled": true,
    "flows_enabled": ["authorization_code"],
    "access_token_alg": "RS256",
    "id_token_alg": "RS256",
    "auth_code_lifetime": 60,
    "access_token_lifetime": 1800,
    "scopes": ["openid", "email", "profile", "groups"],
    "default_scopes": ["openid", "email", "profile", "groups"],
    "challenges": ["S256"],
    "force_mfa": false,
    "client_uri": "https://$domain/",
    "restrict_group_prefix": "bzscraper"
  }
]
JSON
echo "made    secrets/rauthy-bootstrap (the group bzscraper and the client for https://$domain)"
chmod 600 secrets/* secrets/rauthy-bootstrap/* 2>/dev/null || true
chmod 700 secrets/rauthy-bootstrap
