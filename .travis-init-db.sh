#!/bin/bash -xeu

# Database setup for tests.

psql -c "CREATE USER db_user;" -U postgres
psql -c 'CREATE DATABASE rems_test OWNER db_user;' -U postgres
