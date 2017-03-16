# Resource Entitlement Management System

... prototype in Clojure.

## Getting started

### Getting a database

Run the official postgres docker image and initialize the database:

```
./dev_db.sh
```

Which does roughly the following:

```
docker run --rm --name rems_test -p 5432:5432 -d postgres
sleep 5
PGHOST=localhost ./create-test-db.sh
```

When done you can stop (and automatically remove) the database.

```
docker stop rems_test
```

### Running interactively

```
lein repl

rems.standalone=> (start-app)
```

or alternatively

```
lein run
```

Point your browser to <http://localhost:3000>

### Running tests

To run unit tests:

```
lein test
```

To run tests that need a database:

```
lein test :all
```

To run all tests and output coverage:

```
lein with-profile test cloverage
```

## Component Guide

You can access the component guide at `/guide`. It contains all the components in various configurations.

## Operations

### Transfering old data

You can run the transfer script and transfer MariaDB dumps from old REMS using the script `transfer-db.sh`.

First you must create the `transfer` schema to the target Postgres. You can create it like so:
```
psql -U postgres -c "CREATE SCHEMA transfer AUTHORIZATION rems" rems
```

Or in the case of the development docker setup like so:
```
docker run -it --rm postgres psql -h 172.17.0.2 -U postgres -c 'DROP SCHEMA IF EXISTS transfer CASCADE; CREATE SCHEMA transfer AUTHORIZATION rems;' rems;
```

Then you can proceed with loading the dump.

You must provide the name of the dump file, as well as the target database connection details. Docker is required for this script to work.

Transfer script
1. starts a `mariadb` docker image,
1. loads the dump in there, then
1. uses `pgloader` to transfer the data.


Not it may take up to 5 minutes for the DB caches to reload, and reload e.g. localization texts. To force this you can restart the server.

```
PGHOST=172.17.0.2 PGUSER=rems ./transfer-db.sh demo_rems-25-Jan-2017.sql
```

For development you can just run:

```
./dev_transfer.sh
```
