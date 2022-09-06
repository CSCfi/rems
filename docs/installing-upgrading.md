This is a short guide for running REMS as an uberjar. We also provide a [Dockerfile](../Dockerfile) for running REMS with docker.

# Installing REMS

1. Get a `rems.jar` file. You can [build it yourself](development.md) or get one from the [GitHub releases page](https://github.com/CSCfi/rems/releases).
1. Write a configuration file. See [configuration.md](configuration.md) for instructions.
1. Set up a PostgreSQL database and create the data model using `migrate` command.
   It'll take you from empty database to the latest. Make sure you configured the connection details in the config file.

        java -Drems.config=path/to/your/rems/config -jar rems.jar migrate
1. Run rems with a command like

        java -Drems.config=path/to/your/rems/config -jar rems.jar run

# Administering REMS

Some pointers for common tasks:

- **API keys** for HTTP API access can be added with the command

        java -Drems.config=path/to/your/rems/config -jar rems.jar api-key add <key>

  See [using-the-api.md](using-the-api.md) for more info.
- **Users** don't usually need to be added manually as they are automatically created when logging in.
  If you need to add users programmatically, you can use the HTTP API `/api/users/create` as documented in OpenAPI aka Swagger.
  The typical exception is **bot users**, you can read more about them from [bots.md](bots.md).
- **Roles for access control** can be granted with a command like

        java -Drems.config=path/to/your/rems/config -jar rems.jar grant-role <role> <userid>

  - Typically you need to grant at least the `owner` role manually to someone to do administrative tasks through the UI.
  - More info about roles can be found in [glossary.md](glossary.md)
- You should create one or more **organizations** with the owner user with the API or UI. See [organizations.md](organizations.md)
- You can see the full list of CLI administration commands with

        java -Drems.config=path/to/your/rems/config -jar rems.jar help

# Upgrading REMS

1. Get a new `rems.jar`
1. Stop rems.
1. Replace your old `rems.jar` with the new `rems.jar` (you might want to keep a backup of the old `rems.jar`)
1. To migrate data in the database, run rems with the `migrate` command line flag:

        java -Drems.config=path/to/your/rems/config -jar rems.jar migrate

1. Restart rems. Remember to check the log for warnings, they might tell you about compatibility problems with the data or your configuration.

# Running REMS with Docker

We provide example docker-compose files for you to have a look at. 

Generally the database must be started, migrated and populated first.

### Option 1: Run REMS from Docker Hub

This tries to fetch the latest REMS image from Docker Hub. This uses the default `docker-compose.yml` file. It doesn't include files referred to in default config, such as `example-theme/extra-styles.css`, which includes default fonts.

    docker-compose up -d db
    docker-compose run --rm -e CMD="migrate;test-data" app
    docker-compose up -d app
    
### Option 2: Use config file simple-config.edn instead of environment variables

The other build file `docker-compose-config.yml` shows how to configure an external config file named `simple-config.edn`, and custom stylesheet file named `extra-styles.css` which includes default fonts. The files are provided through volumes mounted to the correct place in the container. Don't forget to map all the files you refer to in the config (like `theme.edn`) with similar volumes. Alternatively, you could build your own image on top of our base image, so that it includes the files inside. We prefer to have one image that is same in all environments and use mappings to provide the varying data (config).

    docker-compose -f docker-compose-config.yml up -d db
    docker-compose -f docker-compose-config.yml run --rm -e CMD="migrate;test-data" app
    docker-compose -f docker-compose-config.yml up -d app

### Option 3: Build REMS image locally

The third build file `docker-compose-build-yml` shows how to build the REMS image with [Leiningen](https://leiningen.org/) and `docker-compose`.

    lein uberjar
    docker-compose -f docker-compose-build.yml build
    docker-compose -f docker-compose-build.yml up -d db
    docker-compose -f docker-compose-build.yml run --rm -e CMD="migrate;test-data" app
    docker-compose -f docker-compose-build.yml up -d app

### REMS Commands
The `CMD` environment variable can be used to specify a sequence of REMS administration commands to run. An empty `CMD` (and `COMMANDS`) variable or the command `run` starts the REMS server. Example `CMD` variable values:

    CMD=""
    CMD="migrate;run"
    CMD="migrate;api-key add <api-key>"
    CMD="migrate;test-data;grant-role <role> <userid>;run"

### Access REMS

Point your browser to <http://localhost:3000>

### Shutdown

    docker-compose stop

### Shutdown and remove all data

    docker-compose down
