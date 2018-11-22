#!/bin/bash -xeu

# Start MariaDB
docker run --name rems_mysql --rm -e MYSQL_DATABASE=transfer -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -d mariadb

# Stop (and remove) MariaDB at the end
trap "docker stop rems_mysql" EXIT

# Wait until the database has started
sleep 30

# Load dump
docker run -i --link rems_mysql:mysql --rm mariadb mysql -hmysql -uroot transfer < $1

# Load data from MariaDB into Postgres
docker run --env-file=db.env -i --rm --link rems_mysql:mysql dimitri/pgloader:latest pgloader --set "search_path='transfer'" --verbose mysql://root@rems_mysql/transfer postgresql://$PGUSER@$PGHOST/$PGDATABASE

docker run --env-file=db.env -i --rm postgres:9.5 psql -h $PGHOST -U $PGUSER $PGDATABASE --single-transaction -v ON_ERROR_STOP=ON < resources/sql/transfer-users.sql
