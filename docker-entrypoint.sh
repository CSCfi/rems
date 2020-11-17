#!/bin/bash

[ -z "$COMMANDS" ] && COMMANDS="run"

certfile=$(ls /rems/certs 2>/dev/null)
parameters=false
cmd_prefix=""
cmd=""
FULL_COMMAND=""

if [ ! -z ${certfile} ] && [ "${certfile}" != "null" ] ; then
    keytool -importcert -cacerts -noprompt \
            -storepass changeit \
            -file /rems/certs/${certfile} \
            -alias ${certfile}

    keytool -storepasswd -cacerts \
            -storepass changeit  \
            -new $(head /dev/urandom | tr -dc A-Za-z0-9 | head -c 20)
fi

for comarg in $COMMANDS
do
    # Check if handling a command with parameters
    if [[ "${comarg}" == \(* ]] ; then
        parameters=true
        cmd="${comarg#"("}"
        continue
    # Handle parameter instead of command
    elif [ "${parameters}" = true ] ; then
        cmd+=" ${comarg%")"}"
        if [[ "${comarg}" == *\) ]] ; then
        parameters=false
        else
            continue
        fi
    # Handle run command
    elif [ "${comarg}" = "run" ] ; then
        cmd_prefix="exec"
        cmd=""
    # All other commands
    else
        cmd="${comarg}"
    fi

    FULL_COMMAND="${cmd_prefix} java --illegal-access=deny -Drems.config=config/config.edn -jar rems.jar ${cmd}"
    echo "####################"
    echo "########## RUNNING COMMAND: ${FULL_COMMAND}"
    echo "####################"
    ${FULL_COMMAND}
    cmd=""
done

echo "####################"
echo "########## CONTAINER STARTUP FINISHED"
echo "####################"
