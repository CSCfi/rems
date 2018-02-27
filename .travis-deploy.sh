#!/bin/bash -xeu

# Run as the after_success step of a travis build.

if [[ ( "$TRAVIS_BRANCH" == "master" || "$TRAVIS_BRANCH" == "spa" ) && "$TRAVIS_PULL_REQUEST" == "false" ]]; then
    cd ansible/
    ansible-playbook -vv rems.yml
fi
