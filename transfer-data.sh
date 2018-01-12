#!/bin/bash -xeu

# Start MariaDB
docker run --name rems_mysql -p 3306:3306 --rm -e MYSQL_DATABASE=transfer -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -d mariadb

# Wait until the database has started
sleep 30

# Load dump
docker run -i --link rems_mysql:mysql --rm mariadb mysql -hmysql -uroot transfer < $1

# Load data from MariaDB into Postgres
docker run --env-file=db.env -i --rm --link rems_mysql:mysql dimitri/pgloader pgloader --set "search_path='transfer'" --verbose mysql://root@rems_mysql/transfer postgresql://$PGUSER@$PGHOST/$PGDATABASE

docker run --env-file=db.env -i --rm postgres psql -h $PGHOST -U $PGUSER $PGDATABASE --single-transaction -v ON_ERROR_STOP=ON < resources/sql/transfer-data.sql

# Stop (and remove) MariaDB
docker stop rems_mysql
