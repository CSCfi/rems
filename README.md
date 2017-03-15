# Resource Entitlement Management System

... prototype in Clojure.

## Getting started

### Getting a database

Run the official postgres docker image and initialize the database:

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

You must provide the name of the dump file, as well as the target database connection details. Docker is required for this script to work.

Transfer script
1. starts a `mariadb` docker image,
1. loads the dump in there, then
1. creates `rems_transfer` schema into postgres if it doesn't exist, and finally
1. uses `pgloader` to transfer the data.

```
PGHOST=172.17.0.2 PGUSER=rems ./transfer-db.sh
```
