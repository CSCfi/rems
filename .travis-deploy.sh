#!/bin/bash -xeu

# Run as the after_success step of a travis build.
#
# Pushes the `rems` docker image to repository and runs the ansible
# playbook to deploy it.
#
# Assumptions for travis:
# - $DOCKER_REPOSITORY and $AWS_DOCKER_LOGIN_COMMAND are in the environment
# - ssh keys for accessing AWS are set up

if [[ "$TRAVIS_BRANCH" == "master" && "$TRAVIS_PULL_REQUEST" == "false" ]]; then
    docker tag rems:latest $DOCKER_REPOSITORY/rems:latest
    # AWS_DOCKER_LOGIN_COMMAND is secret, stored in travis repository environment
    $AWS_DOCKER_LOGIN_COMMAND
    docker push $DOCKER_REPOSITORY/rems:latest
    cd ansible/
    ansible-playbook -vv rems.yml
fi
