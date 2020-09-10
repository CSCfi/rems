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
    --tag rems:latest \
    --tag rems:${tag2} \
    --tag docker-registry.rahti.csc.fi/rems/rems:${tag1} \
    --tag docker-registry.rahti.csc.fi/rems/rems:${tag2} .

docker login -p $rahtitoken -u unused docker-registry.rahti.csc.fi
docker push docker-registry.rahti.csc.fi/rems/rems:${tag1}
docker push docker-registry.rahti.csc.fi/rems/rems:${tag2}

if [ "${tag1}" == "release" ] ; then
    docker login -u remspush -p ${dockerhub}
    docker push rems:latest
    docker push rems:${tag2}
fi

