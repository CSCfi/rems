#!/bin/bash -xeu
docker run --rm --name rems_test -p 5432:5432 -d postgres
sleep 5
docker run -i --rm --link rems_test postgres psql -h rems_test -U postgres < resources/sql/init.sql
lein run migrate # create schema
