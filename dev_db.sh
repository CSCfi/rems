#!/bin/bash -xeu
docker run --rm --name rems_test -p 5432:5432 -d postgres:9.5
docker run --rm --link rems_test postgres:9.5 /bin/bash -c "while ! psql -h rems_test -U postgres -c 'select 1;' 2>/dev/null; do sleep 1; done"
docker run -i --rm --link rems_test postgres:9.5 psql -h rems_test -U postgres < resources/sql/init.sql
lein run migrate # create schema
