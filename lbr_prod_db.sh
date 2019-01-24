#!/bin/bash -xeu
docker run --rm --name lbr_rems2 -p 5432:5432 -d postgres:9.6
docker run --rm --link lbr_rems2 postgres:9.6 /bin/bash -c "while ! psql -h lbr_rems2 -U postgres -c 'select 1;' 2>/dev/null; do sleep 1; done"
docker run -i --rm --link lbr_rems2 postgres:9.6 psql -h lbr_rems2 -U postgres < resources/sql/lbr_init.sql
#lein run migrate # create schema
