#!/bin/bash -xeu
# Usage: dev_transfer.sh rems_users_dump.sql rems_data_dump.sql
rems_test_ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' rems_test)
PGHOST=$rems_test_ip PGUSER=rems PGDATABASE=rems ./transfer-users.sh $1
PGHOST=$rems_test_ip PGUSER=rems PGDATABASE=rems ./transfer-data.sh $2
