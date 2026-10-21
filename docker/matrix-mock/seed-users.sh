#!/bin/sh

set -eu

SYNAPSE_URL="http://synapse:8008"
CONFIG_FILE="/data/homeserver.yaml"
USERS_FILE="/seed/users.csv"
REGISTER_USER_BIN="/usr/local/bin/register_new_matrix_user"

if [ ! -x "$REGISTER_USER_BIN" ]; then
  REGISTER_USER_BIN="register_new_matrix_user"
fi

if [ ! -f "$USERS_FILE" ]; then
  echo "users file not found: $USERS_FILE"
  exit 1
fi

echo "Waiting for Synapse at $SYNAPSE_URL ..."
python - <<'PY'
import sys
import time
import urllib.request

url = "http://synapse:8008/_matrix/client/versions"

for _ in range(120):
    try:
        with urllib.request.urlopen(url, timeout=2) as response:
            if response.status == 200:
                sys.exit(0)
    except Exception:
        pass
    time.sleep(1)

sys.exit(1)
PY

echo "Synapse is up. Seeding users..."

while IFS=':' read -r username password role
do
  if [ -z "$username" ]; then
    continue
  fi

  case "$username" in
    \#*)
      continue
      ;;
  esac

  admin_flag="--no-admin"
  if [ "$role" = "admin" ]; then
    admin_flag="-a"
  fi

  if output=$($REGISTER_USER_BIN -u "$username" -p "$password" $admin_flag -c "$CONFIG_FILE" "$SYNAPSE_URL" </dev/null 2>&1); then
    echo "Seeded user '$username'"
  else
    case "$output" in
      *"already taken"*)
        echo "Skipping '$username' (already exists)."
        ;;
      *)
        echo "$output"
        exit 1
        ;;
    esac
  fi
done < "$USERS_FILE"

echo "User seeding complete."
