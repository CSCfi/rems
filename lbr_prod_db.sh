#!/bin/bash -xeu
docker run --rm --name lbr_rems2 -p 5432:5432 -d postgres:9.6
docker run --rm --link lbr_rems2 postgres:9.6 /bin/bash -c "while ! psql -h lbr_rems2 -U postgres -c 'select 1;' 2>/dev/null; do sleep 1; done"

echo "
CREATE USER lbr_rems2_user;
CREATE DATABASE lbr_rems2 OWNER lbr_rems2_user;

CREATE USER lbr_rems2_read;
CREATE USER lbr_rems2_admin;
" | docker run -i --rm --link lbr_rems2 postgres:9.6 psql -h lbr_rems2 -U postgres


