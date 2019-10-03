---
---

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
