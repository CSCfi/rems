#!/bin/bash
set -e

echo "$PRIVATE_KEY" > /rems/keys/private-key.jwk
echo "$PUBLIC_KEY" > /rems/keys/public-key.jwk

# Interpolate secrets and config into config.edn
envsubst < /rems/config/config.edn.template > /rems/config/config.edn

certfile=$(ls /rems/certs 2>/dev/null)
parameters=false
cmd_prefix=""
cmd=""
full_cmd=""
declare -a cmd_array

if [ ! -z ${certfile} ] && [ "${certfile}" != "null" ] ; then
    keytool -importcert -cacerts -noprompt \
            -storepass changeit \
            -file /rems/certs/${certfile} \
            -alias ${certfile}

    keytool -storepasswd -cacerts \
            -storepass changeit  \
            -new $(head /dev/urandom | tr -dc A-Za-z0-9 | head -c 20)
fi

if [ "${CMD}" ] ; then
  IFS=';' read -r -a cmd_array <<< "${CMD}"
elif [ "${COMMANDS}" ] ; then
  IFS=' ' read -r -a cmd_array <<< "${COMMANDS}"
else
  cmd_array=("run")
fi

for cmd in "${cmd_array[@]}"
do
    [ "${cmd}" = "run" ] && cmd_prefix="exec"

    FULL_COMMAND="${cmd_prefix} java -Drems.config=config/config.edn -jar rems.jar ${cmd}"
    echo "####################"
    echo "########## RUNNING COMMAND: ${FULL_COMMAND}"
    echo "####################"
    ${FULL_COMMAND}
done

echo "####################"
echo "########## CONTAINER STARTUP FINISHED"
echo "####################"
