#!/bin/bash -xeu
docker run --rm --name rems_test -p 5432:5432 -d postgres:9.6
docker run --rm --link rems_test postgres:9.6 /bin/bash -c "while ! psql -h rems_test -U postgres -c 'select 1;' 2>/dev/null; do sleep 1; done"
docker run -i --rm --link rems_test postgres:9.6 psql -h rems_test -U postgres < resources/sql/init.sql
if [ -z "${FOR_TESTS:-}" ]; then
  lein with-profile +test run migrate # create schema
  lein with-profile +test run test-data
fi
