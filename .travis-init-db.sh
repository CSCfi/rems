#!/bin/bash -xeu

# Runs database setup

psql -c "CREATE USER db_user WITH PASSWORD 'db_password';" -U postgres
psql -c 'CREATE DATABASE rems_test OWNER db_user;' -U postgres
