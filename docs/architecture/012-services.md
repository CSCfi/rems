# 012: Service namespaces

Authors: @opqdonut @Macroz

## Background & current state

Currently, we have a _service layer_ of `rems.api.services.X`
namespaces that sit between the `rems.api.X` and `rems.db.X`
namespaces. The pattern is that the API takes in the HTTP request and transforms 
it into ideally one call of the service layer. The service can then call multiple 
different db namespaces to implement the functionality.

The service layer was created in PR #1487 (and expanded subsequent PRs
like #1490 #1491 #1495 #1854) to avoid circular dependencies between
db namespaces. That PR contained some discussion about what the
purpose of the service namespaces should be, but no clear conclusion
was reached.

## Decision

Each service represents the public API for a specific part of REMS that is easy to call
and handles dependencies to other items:
- resource, workflow, ... structure
- joining related items
- process manager and async actions

This public API can be used to easily implement the HTTP API, 
but also from called from other parts of the system 
like command-line or asynchronous event handling.

## Unanswered questions

What to do about circular dependencies between services? 
NB: for the data dependencies we have `rems.api.services.dependencies`.

For example after #2261, `rems.api.blacklist` calls
`rems.api.services.command` and `rems.api.services.blacklist`. This is
because `services.command` already calls `services.blacklist`, so we
can't have `services.blacklist` call `services.command`.
