# Resource Entitlement Management System

... prototype in clojure

## Getting started

### Getting a database

Run the official postgres docker image and initialize the database:

```
docker run --rm --name rems_test -p 5432:5432 -d postgres
./create-test-db.sh
lein run migrate
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

### Running via uberjar

```
lein uberjar
java -jar target/uberjar/rems.jar
```

Point your browser to <http://localhost:3000>

### Running via docker

Look up the ip address of your postgres docker with `docker info rems_test`. Replace `172.17.0.2` below with the ip address:

```
lein uberjar
docker build . -t rems
docker run --name rems -p 127.0.0.1:3000:3000 -e DATABASE_URL=postgres://172.17.0.2/rems_test?user=rems_test -d rems
```

Point your browser to <http://localhost:3000>

## Component Guide

You can access the component guide at `/guide`. It contains all the components in various configurations.
