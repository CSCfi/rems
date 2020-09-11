This is a short guide for running REMS as an uberjar. We also provide a [Dockerfile](../Dockerfile) for running REMS with docker.

# Installing REMS

1. Set up a PostgreSQL database.
1. Get a `rems.jar` file. You can build it yourself or get one from the [GitHub releases page](https://github.com/CSCfi/rems/releases).
1. Write a configuration file. See [configuration.md](configuration.md) for instructions.
1. Run rems with a command like

        java -Drems.config=path/to/your/rems/config -jar rems.jar

# Administering REMS

Some pointers for common tasks:

- API keys for HTTP API access can be added with the command

        java -Drems.config=path/to/your/rems/config -jar rems.jar api-key add <key>

  See [using-the-api.md](using-the-api.md) for more info.
- Users don't usually need to be added manually as they are automatically created when logging in.
  If you need to add users programmatically, you can use the HTTP API `/api/users/create` as documented in OpenAPI aka Swagger.
- Roles can be granted with a command like

        java -Drems.config=path/to/your/rems/config -jar rems.jar grant-role <role> <userid>

  Typically you need to grant at least the `owner` role manually to someone to do administrative tasks through the UI.
- More info about roles can be found in [glossary.md](glossary.md)
- If you wish to use multiple organizations, you should create them with the owner user with the API or UI. See [organizations.md](organizations.md)
- You can see the full list of CLI administration commands with

        java -Drems.config=path/to/your/rems/config -jar rems.jar help

# Upgrading REMS

1. Get a new `rems.jar`
1. Stop rems.
1. Replace your old `rems.jar` with the new `rems.jar` (you might want to keep a backup of the old `rems.jar`)
1. To migrate data in the database, run rems with the `migrate` command line flag:

        java -Drems.config=path/to/your/rems/config -jar rems.jar migrate

1. Restart rems. Remember to check the log for warnings, they might tell you about compatibility problems with the data or your configuration.

# Running the application with Docker

### Option 1: Run rems from dockerhub

    docker-compose up -d db
    docker-compose run --rm -e COMMANDS="migrate test-data" app
    docker-compose up -d

### Option 2: Build rems image locally

    lein uberjar
    docker-compose -f docker-compose-build.yml build
    docker-compose -f docker-compose-build.yml up -d db
    docker-compose -f docker-compose-build.yml run --rm -e COMMANDS="migrate test-data" app
    docker-compose -f docker-compose-build.yml up -d

### Access rems

    Point your browser to <http://localhost:3000>

### Shutdown

    docker-compose stop

### Shutdown and remove all data

    docker-compose down

