# 010: Database transactions

Authors: @opqdonut

## Introduction

This is an ADR written in aftermath to a production incident to
describe our current design choices with database transactions.

## Design philosophy

At this point in the software lifecycle of REMS, we err on the side of
caution and try to guarantee consistency of data at the cost of
performance.

## Every API call is a transaction

The `rems.api/transaction-middleware` middleware wraps each API call
in a transaction. The transactions are run with isolation level
serializable, the strictest serialization level (see [PostgreSQL docs]
for more). Additionally, the transactions for GET requests are
read-only to guarantee that our GETs are pure.

[PostgreSQL docs]: https://www.postgresql.org/docs/9.6/transaction-iso.html

## Strict ordering of commands via locking

Our application commands are processed roughly in the following way
(implementation: `rems.api.services.command/command!`):

1. get all events for application from the `application_event` table
2. compute application state
3. run command handler (given command and application state)
4. if command handler succeeds, append new events to `application_event`
5. run process managers on new events, generating e.g. rows in the outbox table

If we were just running commands in parallel with serializable
transactions, we'd get transaction conflicts from the events added in
step 4 (TODO verify this is where the conflicts come from!). To
guarantee that command transactions are processed sequentially, we've
added a

    LOCK TABLE application_event IN SHARE ROW EXCLUSIVE MODE

statement before step 1. This means that further command handlers will
wait for the lock before progressing.

## Future work

We should handle transaction conflict exceptions. There is no
guarantee that they will not happen with isolation level serializable.

We should investigate setting the
`idle_in_transaction_session_timeout` option for our database
connections to avoid e.g. stuck threads rendering the whole system
unusable.

We have considered setting `lock_timeout` to try to avoid production
problems with stuck threads & connections running out. Investigate if
this really helps, and consider `idle_in_transaction_session_timeout`
instead.
