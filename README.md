# Resource Entitlement Management System

... prototype in Clojure.

## Getting started

### Getting a database

Run the official postgres docker image and initialize the database:

```
./dev_db.sh
```

Which does roughly the following:

1. run a postgres container named `rems_test`
2. initialize the database with `resources/sql/init.sql`
3. create the schema with `lein run migrate`

When done you can stop (and automatically remove) the database.

```
docker stop rems_test
```

### Populating the database

- You can get some nice fake data with `lein run test-data`
- You can get an sql dump from the old system and use `./dev_transfer.sh dump.sql`

### Running the application

REMS is a Clojure+Clojurescript Single Page App.

To start the (clojure) backend:

```
lein run
```

To start the (clojurescript) frontend, run in another terminal:

```
lein figwheel
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

You can access the component guide at `/#/guide`. It contains all the
components in various configurations.

## Deployment

See [docs/server_maintenance.md](docs/server_maintenance.md)

## More documentation

See the [docs](./docs) folder
