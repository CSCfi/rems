#!/bin/bash -xeu

# Database setup for tests:
psql -c "CREATE USER rems_test;" -U postgres
psql -c 'CREATE DATABASE rems_test OWNER rems_test;' -U postgres

# Database setup for dev running:
psql -c "CREATE USER rems;" -U postgres
psql -c 'CREATE DATABASE rems OWNER rems;' -U postgres
lein do run migrate, run populate
