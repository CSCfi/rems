#!/bin/bash -xeu
docker run --rm --name rems_test -p 5432:5432 -d postgres
sleep 5
PGHOST=localhost ./create-test-db.sh
docker run -it --rm postgres psql -h 172.17.0.2 -U postgres -c 'DROP SCHEMA IF EXISTS transfer CASCADE; CREATE SCHEMA transfer AUTHORIZATION rems;' rems;
