#!/bin/bash -xeu
# Usage: dev_transfer.sh rems_dump.sql
rems_test_ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' rems_test)
PGHOST=$rems_test_ip PGUSER=rems ./transfer-db.sh $1
