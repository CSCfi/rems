# 012: Service namespaces

Authors: @opqdonut @Macroz

## Background & current state

Currently, we have a _service layer_ of `rems.api.services.X`
namespaces that sit between the `rems.api.X` and `rems.db.X`
namespaces. The pattern is that the api calls the service, and the
service can call multiple different db namespaces.

The service layer was created in PR #1487 (and expanded subsequent PRs
like #1490 #1491 #1495 #1854) to avoid circular dependencies between
db namespaces. That PR contained some discussion about what the
purpose of the service namespaces should be, but no clear conclusion
was reached.

## Decision

Each service represents the public API for a specific part of REMS.
This public API can be used to implement the HTTP API, but also from
other parts of the system.

## Unanswered questions

What to do about circular dependencies between services?
