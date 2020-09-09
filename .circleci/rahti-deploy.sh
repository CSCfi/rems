#!/bin/bash

set -euxo pipefail

instance=${1}

if [ "${instance}" == "demo" ] ; then
    token=${rahti_demo_pod_delete_token}
elif [ "${instance}" == "dev" ] ; then
    token=${rahti_dev_pod_delete_token}
else
    echo "ERROR: Only \"dev\" and \"demo\" arguments supported!"
    echo "USAGE: ${0} [ dev | demo ]"
    exit 1
fi

CONTAINER=$(curl -k -s \
    -H "Authorization: Bearer ${token}" \
    -H 'Accept: application/json' \
    https://rahti.csc.fi:8443/api/v1/namespaces/rems-${instance}/pods | \
    jq '.items | .[].metadata.name' | grep rems-${instance} | tr -d \")

if echo ${CONTAINER} | grep " " ; then
    echo "ERROR: Multiple \"rems-${instance}\" pods returned from rahti!"
    exit 1
elif [ "${CONTAINER}" == "" ] ; then
    echo "ERROR: No \"rems-${instance}\" pods returned from rahti!"
    exit 2
else
    curl -k -s \
        -X DELETE \
        -H "Authorization: Bearer ${token}" \
        -H 'Accept: application/json' \
        https://rahti.csc.fi:8443/api/v1/namespaces/rems-${instance}/pods/${CONTAINER}
fi
