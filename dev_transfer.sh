#!/bin/bash -xeu
rems_test_ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' rems_test)
PGHOST=$rems_test_ip PGUSER=rems ./transfer-db.sh demo_rems-25-Jan-2017.sql
