#!/bin/bash -xeu
docker run --rm --name rems_test -p 127.0.0.1:5432:5432 -d -e POSTGRES_HOST_AUTH_METHOD=trust postgres:13
docker run --rm --link rems_test postgres:13 /bin/bash -c "while ! psql -h rems_test -U postgres -c 'select 1;' 2>/dev/null; do sleep 1; done"
docker run -i --rm --link rems_test postgres:13 psql -h rems_test -U postgres < resources/sql/init.sql
if [ -z "${FOR_TESTS:-}" ]; then
  lein run migrate # create schema
  lein run test-data
fi
