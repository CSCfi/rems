-- Create users, dbs and schemas

CREATE USER rems_test;
CREATE DATABASE rems_test OWNER rems_test;

CREATE USER rems;
CREATE DATABASE rems OWNER rems;

\connect rems
DROP SCHEMA IF EXISTS transfer CASCADE;
CREATE SCHEMA transfer AUTHORIZATION rems;
