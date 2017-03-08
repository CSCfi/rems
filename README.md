# Resource Entitlement Management System

... prototype in clojure

## Getting started

### Getting a database

Run the official postgres docker image and initialize the database:

```
docker run --rm --name rems_test -p 5432:5432 -d postgres
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
