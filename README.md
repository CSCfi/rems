# Resource Entitlement Management System

... prototype in clojure

## Getting started

### Running interactively

```
lein repl

rems.standalone=> (start-app [])
```

Point your browser to <http://localhost:3000>

### Running via uberjar

```
lein uberjar
java -jar target/uberjar/rems.jar
```

Point your browser to <http://localhost:3000>

### Running via docker

```
lein uberjar
docker build . -t rems
sudo docker run -p 127.0.0.1:3000:3000 -d rems
```

Point your browser to <http://localhost:3000>

### Running PostgreSQL inside a local docker container

First run the official postgres docker image. Also initialize the database. For running REMS you also need to provide the environment variable in one way or another.

```
docker run --rm --name rems_test -p 5432:5432 -e POSTGRESS_PASSWORD=db_password -e POSGRES_USER=db_user -d postgres

export PGHOST=localhost

./.travis-init-db.sh

export DATABASE_URL='postgresql://localhost/rems_test?user=db_user&password=db_password'
```

Now your database is set. Running the database tests is possible.

```
lein run test :all
```

Or connect to it using `psql`

```
psql -U db_user rems_test
```

When done you can stop (and automatically remove) the database.

```
docker stop rems_test
```

## Component Guide

You can access the component guide at `/guide`. It contains all the components in various configurations.
