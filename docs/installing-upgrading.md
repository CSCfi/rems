This is a short guide for running REMS as an uberjar. We also provide a [Dockerfile](../Dockerfile) for running REMS with docker.

# Installing REMS

1. Set up a PostgreSQL database.
1. Get a `rems.jar` file. You can build it yourself or get one from the [GitHub releases page](https://github.com/CSCfi/rems/releases).
1. Write a configuration file. See [configuration.md](configuration.md) for instructions.
1. Run rems with a command like `java -Drems.config=path/to/your/rems/config rems.jar`.

# Upgrading REMS

1. Get a new `rems.jar`
1. Stop rems.
1. Replace your old `rems.jar` with the new `rems.jar` (you might want to keep a backup of the old `rems.jar`)
1. To migrate data in the database, run rems with the `migrate` command line flag: `java -Drems.config=path/to/your/rems/config rems.jar migrate`
1. Restart rems. Remember to check the log for warnings, they might tell you about compatibility problems with the data or your configuration.
