# Getting started with REMS development

## Development database

Run the official postgres docker image and initialize the database by running

```
./dev_db.sh
```

Which does roughly the following:

1. run a postgres container named `rems_test`
2. initialize the database with `resources/sql/init.sql`
3. create the schema and populates it with test data

When done you can stop (and automatically remove) the database.

```
docker stop rems_test
```

## Populating the database

- You can get some test data with `lein run test-data`

## Running the application

To start the (clojure) backend:

```
lein run
```

To start the (clojurescript) frontend, run in another terminal:

```
lein figwheel
```

Point your browser to <http://localhost:3000>

## Running tests

To run unit tests:

```
lein kaocha unit
```

To run tests that need a database:

```
lein kaocha integration
```

To run a specific test you can use:

```
lein kaocha --focus 'rems.test-browser/test-form-editor'
```

### Browser tests

To run build the JS bundle and run browser tests (requires chromedriver in $PATH, the alias also builds cljs):

```
lein browsertests
```

If browser tests fail, screenshots and DOM are written in the directory `browsertest-errors`.

For fixing or especially the development of the browser tests, you can run a windowed regular browser and see what the tests are doing.

1. Open the `test_browser.clj`.
2. Use the code `(init-driver! :chrome "http://localhost:3000/" :development)` to open up the controller browser instance
3. Evaluate any test code in your editor to see the actions happen in the browser.

### Clojurescript tests

First make sure you have the npm depenencies with

```
lein deps
```

and then just run

```
lein doo once
```

to run tests in Headless Chrome via Karma.

You may need to run `npm install karma karma-cljs-test karma-chrome-launcher` first to install the necessary tools and packages.

### Running all the tests

To conveniently run all the tests you can run the lein alias

```
lein alltests
```

## Indentation & formatting

We use [cljfmt](https://github.com/weavejester/cljfmt) for checking & fixing code formatting issues.

Use `lein cljfmt check` to check your formatting. Use `lein cljfmt
fix` or `lein cljfmt fix <file>` to automatically fix your formatting.

## Component Guide

You can access the component guide at `/guide`. It contains all the
components in various configurations.
