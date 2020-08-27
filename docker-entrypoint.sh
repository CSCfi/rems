#!/bin/bash

[ -z "$COMMANDS" ] && COMMANDS="run"

certfile=$(ls /rems/certs)

if [ ! -z ${certfile} ] && [ "${certfile}" != "null" ] ; then
    keytool -importcert -cacerts -noprompt \
            -storepass changeit \
            -file /rems/certs/${certfile} \
            -alias ${certfile}

    keytool -storepasswd -cacerts \
            -storepass changeit  \
            -new $(head /dev/urandom | tr -dc A-Za-z0-9 | head -c 20)
fi

for COMMAND in $COMMANDS
do
    if [ "${COMMAND}" = "run" ] ; then
        FULL_COMMAND="exec java --illegal-access=deny -Drems.config=config/config.edn -jar rems.jar"
    else
        FULL_COMMAND="java --illegal-access=deny -Drems.config=config/config.edn -jar rems.jar ${COMMAND}"
    fi
    echo "####################"
    echo "########## RUNNING COMMAND: ${FULL_COMMAND}"
    echo "####################"
    ${FULL_COMMAND}
done
echo "####################"
echo "########## CONTAINER STARTUP FINISHED"
echo "####################"

tail -f /dev/null
