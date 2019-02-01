# 001: Event and API versioning

Authors: @opqdonut @Macroz @luontola @cscwooller

## Event versioning

Events won't be versioned for now. Instead, we'll use (migratus)
migrations to always bring the events up to the latest version. The
migration tool state will be the implicit version number.

NB! migrations that split events are a bit complicated since events
are stored in-order. The migration will need to create a new event
table, populate it with the split events, and then copy the events
back to the original table, preserving order.

We should implement a script that takes a DB dump from production,
runs the event (and normal) migrations on it, and verifies data
integrity. See issue #899

## API versioning

We want to make backwards-compatible APIs. This means that breaking
changes (removing an output field, adding a mandatory input field,
etc.) should be done by creating a new api, with a higher version
number.

How long old versions of APIs are supported needs to be decided
case-by-case. The decisions should be documented in the release notes
so that users can adapt.

We should implement some sort of automated test that highlights
breaking api changes. See issue #898

## Event and API schemas

We now have schemas for (storing) events (see #881). We discussed
whether we should have a separate event schema for the API. In the end
we decided to try to use event schemas in the API as much as possible.

When returning events from the API we sometimes need to censor them.
We prefer to do this by dropping events instead of removing fields.
This way the same schema can be used.

NB! when you update an event schema, you might cause a breaking change
in the API. This will hopefully be caught by the automatic test from
the previous section.
