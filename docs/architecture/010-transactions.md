# 010: Database transactions

Authors: @opqdonut @Macroz

## Introduction

This is an ADR written in aftermath to a production incident [Issue #2102]
to describe our current design choices with database transactions.

[Issue #2102]: https://github.com/CSCfi/rems/issues/2101

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
(implementation: `rems.service.command/command!`):

1. get all events for application from the `application_event` table
   - NB! we have caching for `get-all-applications` but not for `get-unrestricted-application`, which is used here
2. compute application state
3. run command handler (given command and application state)
4. if command handler succeeds, append new events to `application_event`
5. run process managers on new events, which can result in
   - new commands (e.g. from `approver-bot`), which are processed immediately in the same thread
   - new rows in db (e.g. `outbox` or `blacklist` rows)

If we were just running commands in parallel with serializable
transactions, we'd get transaction conflicts from the events added in
step 4 (TODO verify this is where the conflicts come from!). To
guarantee that command transactions are processed sequentially, we've
added a

    LOCK TABLE application_event IN SHARE ROW EXCLUSIVE MODE

statement before step 1. This means that further command handlers will
wait for the lock before progressing.

## Problems with locks

There have been a couple of incidents in production where for some
reason a thread got stuck holding the lock on `application_table`.
This prevented other commands from being processed. Additionally, the
only thing communicated to the users was an eventual HTTP timeout,
which they might've not had the patience to wait for.

To protect against failures like this, we set two [PostgreSQL config
variables] for every connection (implementation: `rems.db.core`):
- `lock_timeout` to 10 seconds
- `idle_in_transaction_session_timeout` to 20 seconds

This guarantees that if a thread is stuck holding the lock, other
commands will fail within 10 seconds (with a HTTP 503 Service
Unavailable), and the connection that the stuck thread is holding will
get closed after 20 seconds, freeing the lock.

Implementation in [PR #2100]

[PostgreSQL config variables]: https://www.postgresql.org/docs/9.6/runtime-config-client.html
[PR #2100]: https://github.com/CSCfi/rems/pull/2100

## Future work

We should handle transaction conflict exceptions. There is no
guarantee that they will not happen with isolation level serializable. [Issue #2103].

[Issue #2103]: https://github.com/CSCfi/rems/issues/2103

In addition to API calls, scheduled jobs like email outbox processing
should also be in transactions. [Issue #2104].

[Issue #2104]: https://github.com/CSCfi/rems/issues/2104

Consider adding some sort of warning/error when a rems.db.core
function is called outside a transaction. This can be done with e.g. a
custom `bind-connection` macro. [Issue #2105].

[Issue #2105]: https://github.com/CSCfi/rems/issues/2105
