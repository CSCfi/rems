# Getting started with REMS development

## Installed Software

In order to get started with REMS, you need to have the following software installed:

   - Docker
   - Java. Currently REMS should be working with Java versions 11 to 25
   - Leiningen. As of 2026-01-08, the project should work with lein version Leiningen 2.12.0 on Java 25 OpenJDK 64-Bit Server VM.
   - npm and npx

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

To start the (Clojure) backend:

```
lein run
```

To start the (ClojureScript) frontend, run in another terminal:

```
lein shadow-watch
```

Normally we run the application in port 3000. Point your browser to <http://localhost:3000>.
This supports live reload. Shadow-CLJS also exposes port 3100 for development.
The API and the static files are all served from the port 3000.
Shadow-CLJS has its own console in <http://localhost:9630>.

### Editor setup

You can also use e.g. Emacs with CIDER integration and `cider-jack-in-clj&cljs`. Calva and Cursive have also been used successfully. And we regularly use Linux and Mac for development. YMMV.

In whatever editor you decide, you should start in the development profile, i.e., in Emacs set `Cider Lein Parameters` to `with-profile +dev repl :headless`.

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

As the tests run, and especially if browser tests fail, screenshots and DOM are written in the directory `browsertest-errors`.

For fixing or especially the development of the browser tests, you can run a windowed regular browser and see what the tests are doing.

1. Open the `test_browser.clj`.
2. Use the code `(init-driver! :chrome "http://localhost:3000/" :development)` to open up the controller browser instance
3. Evaluate any test code in your editor to see the actions happen in the browser.

Alternatively, you can set the environment variable `HEADLESS` to `0` to see the tests while they are running:

```
HEADLESS=0 lein browsertests                                     # to see all browser test
HEADLESS=0 lein kaocha --focus rems.test-browser/test-blacklist  # to see a specific browser test
```

### ClojureScript tests

We have some tests for specifically ClojureScript code. A helper runs them with

```
lein shadow-test
```

They will run the tests in a headless Chrome via Karma and Shadow-CLJS.

You may need to run `npm install karma karma-cljs-test karma-chrome-launcher` first to install the necessary tools and packages.

Running tests in watch mode requires a background process that watches ClojureScript code and re-compiles on changes. This can be achieved by starting both Karma and Shadow-CLJS in separate processes:

```
npx karma start karma.conf.js
npx shadow-cljs watch cljs-test
```

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

## Dependency updating

Dependencies are declared in 
- `project.clj` includes dependencies built with Leiningen (mostly back-end, some front-end development tooling)
- `shadow-cljs.edn` includes dependencies built with Shadow-CLJS (front-end)
- `package.json` includes Node.js dependencies (front-end)
- `Dockerfile` specifies the base image
- `.circleci/config.yml` includes certain build-time dependencies used by CircleCI

Running `lein antq` produces a list of outdated dependencies by looking at their latest available versions. It can inspect Leiningen, Shadow-CLJS and CircleCI dependencies. 

[`lein ancient`] is also available, but the exclude metadata in `project.clj` is set up for `antq` so `ancient` will report these as outdated.

Consider also installing `antq` as a (Clojure CLI tool)[https://github.com/liquidz/antq?tab=readme-ov-file#clojure-cli-tools-11111139-or-later] locally to be able to use the interactive upgrade dialogue and suppression metadata in `project.clj`. Upgrading deps interactively one by one is useful when running tests in between bumping versions.
```
clojure -Ttools install-latest :lib com.github.liquidz/antq :as antq
clojure -Tantq outdated :upgrade true
```

Node.js dependencies can be inspected with `npm outdated` which looks for latest version, and `npm audit` which checks for outstanding vulnerabilities.

[`lein unused-deps`](https://codeberg.org/technomancy/lein-unused-deps) can be used to find dependencies that aren't referred to in the code and could potentially be removed from `project.clj`.

Finally, to inspect dependencies manually, there are
- `lein deps :tree`
- `lein deps :why <library name>` for why a given library is included
- `lein pom` to write a POM file containing every dependency including transitive Java libraries
-  and `mvn dependency:tree -Dverbose:true` to view the POM file in a tree representation (requires Maven installed)

### Vulnerability scanning

Use [`nvd-clojure`](https://github.com/rm-hull/nvd-clojure) to scan vulnerabilities in the libraries we use. `nvd-clojure` is a Clojure wrapper for [OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/).

The APIs Dependency-Check uses are rate-limited and require authentication. Request credentials for
- The National Vulnerability Database https://nvd.nist.gov/developers/request-an-api-key
- Sonatype OSS Index https://ossindex.sonatype.org/doc/auth-required/

In a non-version-controlled directory, write the config file `nvd-config.edn` and fill in the blanks
```clojure
{:nvd-api {:key ""}
 :analyzer {:ossindex-user ""
            :ossindex-password ""}}
```

Issue the following command substituting `REMS DIR` with the path where you have cloned the REMS repository
```
clojure -J-Dclojure.main.report=stderr -Tnvd nvd.task/check :classpath \""$(cd <REMS DIR>; lein with-profile -user,-dev classpath)\"" :config-filename \""nvd-config.edn\""
```

Whenever vulnerabilities are found in libraries you don't recognise, refer to the previous section for tools to inspect them.

#### Docker

We use [hadolint](https://github.com/hadolint/hadolint) to lint our Dockerfile. Hadolint looks for errors, misconfiguration and enforces certain best practices. Hadolint runs in our CircleCI pipeline, and it is also available as a CLI tool.

Docker image vulnerabilities can be scanned using [Trivy](https://trivy.dev/).


## Component Guide

You can access the component guide at `/guide`. It contains all the UI
components in various configurations and is useful for example to develop and document UI components.

See a running guide as example [https://rems-dev.2.rahtiapp.fi/guide](https://rems-dev.2.rahtiapp.fi/guide).

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

NB: The migrations can also be written in Clojure. Then you should replace the `.sql` files with a `.edn` file. See examples in the migrations-directory.

## Performance testing

REMS includes `user simulator`, which is a developer tool intended for load testing REMS. Currently the tool is fairly basic and does not have a lot of features, but it can be used to simulate multiple users accessing the system simultaneously. Only fake login users are supported currently.

User simulator works by running concurrent headless browser instances on target REMS instance. Starting the simulator from command line is simple:

```sh
lein run user-simulator http://localhost:3000/ alice,elsa,frank
```

This will set local REMS instance as the target and start 3 concurrent browser instances, each logging in as alice, elsa or frank.

## CircleCI

CircleCI offers a [command-line tool](https://circleci.com/docs/guides/toolkit/local-cli/) for debugging and validating the [config file](/.circleci/config.yml) (and much more)
