#!/bin/bash
set -euo pipefail
set -x

# Encode DB password to safely use in JDBC URL
urlencode() {
  local raw="$1"
  local encoded=""
  local i c
  for (( i = 0; i < ${#raw}; i++ )); do
    c="${raw:$i:1}"
    case "$c" in
      [a-zA-Z0-9.~_-]) encoded+="$c" ;;
      *) encoded+=$(printf '%%%02X' "'$c") ;;
    esac
  done
  echo "$encoded"
}

export DB_PASSWORD_ENCODED=$(urlencode "$DB_PASSWORD")

# Write Visa key files
echo "$PRIVATE_KEY" > /rems/keys/private-key.jwk
echo "$PUBLIC_KEY" > /rems/keys/public-key.jwk

# Generate config.edn
envsubst < /rems/config/config.edn.template > /rems/config/config.edn

echo "========================"
echo "Generated config.edn:"
cat /rems/config/config.edn
echo "========================"

# Optional: install custom cert if provided
certfile=$(ls /rems/certs 2>/dev/null || true)
if [ -n "${certfile}" ] && [ "${certfile}" != "null" ]; then
  keytool -importcert -cacerts -noprompt \
          -storepass changeit \
          -file "/rems/certs/${certfile}" \
          -alias "${certfile}"

  keytool -storepasswd -cacerts \
          -storepass changeit \
          -new "$(head /dev/urandom | tr -dc A-Za-z0-9 | head -c 20)"
fi

# Dispatch by CMD
case "${CMD:-start}" in
  migrate)
    echo "########## RUNNING REMS MIGRATION ##########"
    exec java -Drems.config=config/config.edn -jar rems.jar migrate
    ;;
  start)
    echo "########## STARTING REMS ##########"
    exec java -Drems.config=config/config.edn -jar rems.jar run
    ;;
  *)
    echo "Unknown CMD: '${CMD}' — valid options are: start, migrate"
    exit 1
    ;;
esac


echo "####################"
echo "########## CONTAINER STARTUP FINISHED"
echo "####################"
