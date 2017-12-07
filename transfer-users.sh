#!/bin/bash -xeu

# Start MariaDB
docker run --name rems_mysql -p 3306:3306 --rm -e MYSQL_DATABASE=rems_mysql -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -d mariadb

# Wait until the database has started
sleep 30

# Load dump
docker run -i --link rems_mysql:mysql --rm mariadb mysql -hmysql -uroot rems_mysql < $1

# Load data from MariaDB into Postgres
docker run --env-file=db.env -it --rm --link rems_mysql:mysql dimitri/pgloader pgloader --set "search_path='transfer'" --verbose mysql://root@rems_mysql/rems_mysql postgresql://$PGUSER@$PGHOST/$PGDATABASE

docker run --env-file=db.env -i --rm postgres psql -h $PGHOST -U $PGUSER $PGDATABASE < resources/sql/transfer-users.sql

# Stop (and remove) MariaDB
docker stop rems_mysql
