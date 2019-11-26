# 002: Event side effects

Authors: @opqdonut @Macroz @luontola @cscwooller

## Background

Some application events generate side-effects, e.g.
- adding entitlements (and POSTing them elsewhere)
- email notifications

We wanted to come up with an architecture for these.

## Event pollers

For each such side-effect, let's implement an _event poller_: a
scheduled job that walks events in the db, keeping track of which
events it has already seen.

If a side-effect fails, the poller will stop, and retry the same event
later.

Pollers should be separate for testability, because we want to
decouple failures, and perhaps future configurability. We'll of course
have some share infrastructure code e.g. for storing which events have
been processed in the DB.

## Amendment: process managers

Date: 2019-11-21
Authors: @opqdonut @luontola

### Problems with existing pollers

Since one event can generate multiple emails, and only some of these
emails might fail, we shouldn't retry to process the whole event.

The entitlement poller was causing entitlements to not be immediately
available after approving an application, which made implementing
integrations harder.

### New design: process managers and outboxes

A new design is to have _process managers_ that run right after the
command handler. The process managers look at the events produces by
the command, and produce a) new commands b) new database rows.

Since the managers are run synchronously in the command handler, any
side-effects should be quick and not be able to fail. Side-effects
which can fail and might need to be retried are decoupled from
commands with _outboxes_.

An _outbox_ is a database table that stores side-effects that are yet
to happen, e.g. emails to send or entitlements to POST. A scheduled
job (poller) monitors the outbox, tries to send messages, counts
retries, etc.

We'll start off by having separate outboxes for emails and
entitlements. A generic outbox can be implemented later if we get more
types of side effects.

Examples of process managers:
- The approver bot reads submit events and produces approve commands
- The email process manager reads events and writes emails to the
  email outbox in the db

See also:
- #1750: issue about email poller
- #1775: PR refactoring email poller into process manager + outbox
- #1784: issue about refactoring entitlements too

## Amendment: generic outbox

Date: 2019-11-25
Authors: @opqdonut

Instead of the separate outbox tables mentioned in the previous
amendment, it was easier to go directly to a generic outbox table.
