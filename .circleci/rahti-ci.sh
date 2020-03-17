#!/bin/bash

instance=${1}

if [ "${instance}" == "dev" ] ; then
    tag1=circle
    tag2=$(git rev-parse HEAD)
    token=${rahti_dev_pod_delete_token}
elif [ "${instance}" == "dev" ] ; then
    tag1=release
    tag2=$(git describe --abbrev=0 --tags)
    token=${rahti_demo_pod_delete_token}
else
    echo "ERROR: Only \"dev\" and \"demo\" arguments supported!"
    echo "USAGE: ${0} [ dev | demo ]"
    exit 1
fi

docker build --pull \
    --tag docker-registry.rahti.csc.fi/rems/rems:${tag1} \
    --tag docker-registry.rahti.csc.fi/rems/rems:${tag2} .

docker login -p $rahtitoken -u unused docker-registry.rahti.csc.fi
docker push docker-registry.rahti.csc.fi/rems/rems:${tag1}
docker push docker-registry.rahti.csc.fi/rems/rems:${tag2}

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
    echo $CONTAINER
    curl -k -s \
        -X DELETE \
        -H "Authorization: Bearer ${token}" \
        -H 'Accept: application/json' \
        https://rahti.csc.fi:8443/api/v1/namespaces/rems-dev/pods/${CONTAINER}
fi
