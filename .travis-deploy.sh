#!/bin/bash -xeu

# Run as the after_success step of a travis build.
#
# Pushes the `rems` docker image to repository and runs the ansible
# playbook to deploy it.
#
# Assumptions for travis:
# - DOCKER_REPOSITORY, AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY in env
# - ssh keys for accessing AWS are set up

if [[ "$TRAVIS_BRANCH" == "master" && "$TRAVIS_PULL_REQUEST" == "false" ]]; then
    docker tag rems:latest $DOCKER_REPOSITORY/rems:latest
    $(aws ecr get-login)
    docker push $DOCKER_REPOSITORY/rems:latest
    cd ansible/
    ansible-playbook -vv rems.yml
fi
