# Getting started with REMS development

## Installed Software

In order to get started with REMS, you need to have the following software installed:

   - Docker
   - Java. Currently REMS should be working with Java versions 11 to 17
   - leiningen. As of 2021-11-01, the project should work with lein version Leiningen 2.9.7 on Java 17 OpenJDK 64-Bit Server VM.
### Mac OS installation for Apple M1 Chip (Apple Silicon M1)

1. Java Installation: install Java via Azul, make sure you download the version of [Java for MacOS for ARM architecture](https://www.azul.com/downloads/zulu-community/?os=macos&architecture=arm-64-bit&package=jdk).
 
2. Do not install leiningen through homebrew, rather use the script from [here](https://purelyfunctional.tv/guide/how-to-install-clojure/#mac-leiningen) to install leiningen.

## Development database

Run the official postgres docker image and initialize the database by running

```
./dev_db.sh
```

Which does roughly the following:

1. runs a postgres container named `rems_test`
2. initializes the database with `resources/sql/init.sql`
3. creates the schema and populates it with test data

When done you can stop (and automatically remove) the database.

```
docker stop rems_test
```

### Populating the development database

The best setup for development is to populate the database with test data by running

```
lein run test-data
```

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

You can also use e.g. Emacs with CIDER integration and `cider-jack-in-clj&cljs`.

You should start in the development profile i.e. set `Cider Lein Parameters` to `with-profile +dev repl :headless`

We have had success with other editors as well.

## Building an uberjar

To build a deployable uberjar, run

```
lein uberjar
```

after which you can find the jar at `target/uberjar/rems.jar`. See [installing-upgrading.md](installing-upgrading.md) for more info on deploying an uberjar.

## Running tests

To run unit tests:

```
lein kaocha unit
```

To run tests that need a running database:

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

Alternatively, you can set the environment variable `HEADLESS` to `0` to see the tests while they are running:

```
HEADLESS=0 lein browsertests                                     # to see all browser test
HEADLESS=0 lein kaocha --focus rems.test-browser/test-blacklist  # to see a specific browser test
```

### Clojurescript tests

First make sure you have the npm depenencies with

```
lein deps
```

and then just run

```
lein doo once
```

which will run the tests in a headless Chrome via Karma.

You may need to run `npm install karma karma-cljs-test karma-chrome-launcher` first to install the necessary tools and packages.

### Running all the tests

To conveniently run all the tests you can run the lein alias

```
lein alltests
```
### Automated accessibility test report

We use [axe](https://www.deque.com/axe/) for automated accessibility tests.

The preferable way is to run the browser test suite, or let CI run it, and see what is recorded in the `browsertest-accessibility-report` directory, for example `violations.json` file.

By enabling the configuration option `:accessibility-report` you can have the tool running and accessible also from `window.axe` object in the browser console. This should be enabled in the dev and test configs for you.

## Indentation & formatting

We use [cljfmt](https://github.com/weavejester/cljfmt) for checking & fixing code formatting issues.

Use `lein cljfmt check` to check your formatting. Use `lein cljfmt
fix` or `lein cljfmt fix <file>` to automatically fix your formatting.

This setup should correspond to pretty much the default indentation of CIDER in Emacs. Other editors may need to adjust settings here and there, but our CI will help you to spot any mistakes.

## Component Guide

You can access the component guide at `/guide`. It contains all the UI
components in various configurations and is useful for example to develop and document UI components.

See a running guide as example [https://rems-dev.rahtiapp.fi/guide](https://rems-dev.rahtiapp.fi/guide).

## Developing database migrations

We use [Migratus](https://github.com/yogthos/migratus) for database migrations. It supports both SQL and Clojure code-based migrations. See `src/clj/rems/migrations/example.clj` for example on code-based migration.

To create new migration:

```sh
lein migratus create feature-name
```

This will create two new files:
```
resources/migrations/20211105110533-feature-name.down.sql
resources/migrations/20211105110533-feature-name.up.sql
```

To run specific migration down (use only timestamp as identifier):
```sh
lein migratus down 20211105110533
```

To run specific migration up:
```sh
lein migratus up 20211105110533
```