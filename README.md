[![Build Status](https://travis-ci.org/CSCfi/rems.svg?branch=master)](https://travis-ci.org/CSCfi/rems)
# Resource Entitlement Management System

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
lein eftest
```

To run tests that need a database:

```
lein eftest :all
```

To run all tests and output coverage:

```
lein with-profile test cloverage
```

To run browser tests (requires chromedriver in $PATH):

```
lein eftest :browser
```

Start REPL and run tests in there:

```
lein with-profile test repl
(user/run-all-tests)
```

#### Clojurescript tests

First make sure you have the npm depenencies with

```
lein deps
```

and then just run

```
lein doo
```

to run tests in Headless Chrome via Karma.

## Component Guide

You can access the component guide at `/#/guide`. It contains all the
components in various configurations.

## Contributing

REMS is an open source project. In case you would like to contribute to its development, please refer to the [CONTRIBUTING.md](CONTRIBUTING.md) document.

## More documentation

Documentation files can be found under the [docs](./docs) folder.

Documentation can also be read from the browser by launching user docs server with the command:
`mkdocs serve`

Alternatively docker images can be used for running the documentation server:
```
docker build . -f docs-server/Dockerfile -t rems-mkdocs-server
docker run -it -p 8000:8000 --name rems-user-guide rems-mkdocs-server --rm
```

*Note* live reload is disabled for the docker version of mkdocs.
