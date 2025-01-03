#!/bin/bash
set -euxo pipefail

build_type=${1}

if [ "${build_type}" == "circle" ] ; then
    tag1=circle
    tag2=$(git rev-parse HEAD)
elif [ "${build_type}" == "release" ] ; then
    tag1=release
    tag2=$(git describe --tags --exact-match)
else
    echo "ERROR: Only \"circle\" and \"release\" arguments supported!"
    echo "USAGE: ${0} [ circle | release ]"
    exit 1
fi

docker build --pull \
    --tag cscfi/rems:latest \
    --tag cscfi/rems:${tag2} \
    --tag image-registry.apps.2.rahti.csc.fi/rems/rems:${tag1} \
    --tag image-registry.apps.2.rahti.csc.fi/rems/rems:${tag2} .

docker login -p $rahti2token -u unused image-registry.apps.2.rahti.csc.fi
docker push image-registry.apps.2.rahti.csc.fi/rems/rems:${tag1}
docker push image-registry.apps.2.rahti.csc.fi/rems/rems:${tag2}

if [ "${tag1}" == "release" ] ; then
    docker login -u remspush -p ${dockerhub}
    docker push cscfi/rems:latest
    docker push cscfi/rems:${tag2}
fi

