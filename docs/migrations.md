## Operations

### Transfering old data

You can run the transfer script and transfer MariaDB dumps from old REMS using the script

- `transfer-users.sh` transfers users from a Liferay table dump. Run this first!
- `transfer-data.sh` transfers old data from the regular tables and fixes the user keys on the fly

First you must create the receiving `transfer` schema to the target Postgres.

In the case of the development docker setup just run `dev_db.sh`, which initializes a Postgres docker container for you.

Otherwise, i.e. for production, You can create it like so:
```
psql -U postgres -c "CREATE SCHEMA transfer AUTHORIZATION rems" rems
```

Then you can proceed with loading the dump.

You must provide the name of the dump file, as well as the target database connection details. Docker is required for this script to work as temporary instances of databases are created.

Transfer script

1. starts a `mariadb` docker image,
2. loads the dump in there, then
3. uses `pgloader` to transfer the data.

Note it may take up to 5 minutes for the DB caches to reload, and reload e.g. localization texts. To force this you can restart the server.

In case your database requires a password to function, you can provide a password by modifying the db.env file, found in the project root folder, appropriately.

```
PGHOST=172.17.0.1 PGUSER=rems PGDATABASE=rems ./transfer-users.sh richer2_liferay_20171106.sql
PGHOST=172.17.0.1 PGUSER=rems PGDATABASE=rems ./transfer-data.sh richer2_rems_20171106.sql
```

For development you can just run:

```
./dev_transfer.sh rems_liferday_dump_file.sql rems_db_dump_file.sql
```
