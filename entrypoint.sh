#!/bin/bash

cd rems

[ -z "$COMMANDS" ] && COMMANDS="run"

for COMMAND in $COMMANDS
do
    if [ "${COMMAND}" = "run" ] ; then
        COMMAND=""
    fi
    echo "####################"
    echo "########## RUNNING COMMAND: java --illegal-access=deny -Drems.config=config/config.edn -jar rems.jar ${COMMAND}"
    echo "####################"
    java -Drems.config=config/config.edn -jar rems.jar ${COMMAND}
done
    echo "####################"
    echo "########## CONTAINER STARTUP FINISHED"
    echo "####################"

tail -f /dev/null
