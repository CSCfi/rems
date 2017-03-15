#!/bin/bash -xeu

# Start MariaDB
docker run --name rems_mysql -p 3306:3306 --rm -e MYSQL_DATABASE=rems_mysql -e MYSQL_ROOT_PASSWORD=rems_test -d mariadb

# Wait until the database has started
sleep 30

# Load dump
docker run -it --link rems_mysql:mysql -v `readlink -f $1`:/tmp/data.sql --rm mariadb sh -c 'exec mysql -h"$MYSQL_PORT_3306_TCP_ADDR" -P"$MYSQL_PORT_3306_TCP_PORT" -uroot -p"$MYSQL_ENV_MYSQL_ROOT_PASSWORD" rems_mysql < /tmp/data.sql'

# Check contents of MariaDB
#docker run -it --link rems_mysql:mysql --rm mariadb sh -c 'exec mysql -h"$MYSQL_PORT_3306_TCP_ADDR" -P"$MYSQL_PORT_3306_TCP_PORT" -uroot -p"$MYSQL_ENV_MYSQL_ROOT_PASSWORD" --execute="USE rems_mysql; SELECT * from rms_catalogue_item;"'

# Start mysql client
#docker run -it --link rems_mysql:mysql --rm mariadb sh -c 'exec mysql -h"$MYSQL_PORT_3306_TCP_ADDR" -P"$MYSQL_PORT_3306_TCP_PORT" -uroot -p"$MYSQL_ENV_MYSQL_ROOT_PASSWORD"'

# Start postgres client
#docker run -it --rm --link rems_test:postgres postgres psql -h postgres -U rems

# Create target schema in Postgres
#docker run -it --rm postgres psql -h $PGHOST -U postgres -c 'DROP SCHEMA IF EXISTS transfer CASCADE; CREATE SCHEMA transfer;' rems;

# Load data from MariaDB into Postgres
docker run -it --rm --link rems_mysql:mysql dimitri/pgloader pgloader --set "search_path='transfer'" --verbose mysql://root:rems_test@rems_mysql/rems_mysql postgresql://$PGUSER@$PGHOST/rems

docker run -it --rm --link rems_test:postgres -v `readlink -f resources/sql/transfer.sql`:/tmp/transfer.sql postgres psql -h $PGHOST -U $PGUSER -f /tmp/transfer.sql

# Stop (and remove) MariaDB
docker stop rems_mysql
