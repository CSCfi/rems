#!/bin/bash

cd rems

[ -z "$COMMANDS" ] && COMMANDS="run"

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
