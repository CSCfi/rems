# 022: Caches

Status: Draft

Authors: @aatkin

## Problem

- REMS struggled with scalability due to heavy database load, because virtually every db entity was queried from database without caching, e.g. users.
- Every request required role calculations, which needed up-to-date application cache. Problems mounted up whenever application cache needed to be recalculated, due to it locking events table. Application cache was slow to recalculate, and was becoming even slower as event history grew (n+1 problem).
- Original performance test data did not apply correct pressure, because recalculating application cache took around 10 seconds. This was with 1000 applications and 5 events each (5000 events).
- Problems became apparent when the amount of events was scaled up: first 1000 applications with 24 events (24000 events), then 3000 applications with 24 events each (72000 events). Together with increasing workflow handlers from 2 to 100, application cache recalculation took over 30 seconds, which caused database connections to time out.

## Assumptions

- Application events would continue to scale beyond the tested volumes.
- REMS should be expected to handle concurrent requests without deadlocks.
- Any changes to database outside of application code should be handled manually.
- Memory pressure from caches comes mostly from application events.

## Solution

- Unified cache layer for all `rems.db.*` entities, implemented in `rems.cache` namespace.
- Simple API that is easy to understand and use (looks like `clojure.core.cache`), easy to test, and allows partial cache updates.
- Supports read-only caches, which are automatically invalidated when the depended cache is updated. Dependencies are tracked using a DAG (directed acyclic graph).
- Using performance data that consists of 3000 applications, over 72000 events, 1000 users and workflow with 100 handlers, updating workflow that triggers recalculation of 2999 applications takes around 5 seconds (on lower-end CPU) without significantly affecting other requests.
- Memory usage: 3000 applications, 72000 events, 1000 users, 1000 resources and 1000 catalogue items fit in 2GB of RAM.

DB namespaces that initially implement caches:
- `rems.db.attachments`
- `rems.db.blacklist`
- `rems.db.catalogue`
- `rems.db.category`
- `rems.db.form`
- `rems.db.licenses`
- `rems.db.organizations`
- `rems.db.resource`
- `rems.db.roles`
- `rems.db.user-mappings`
- `rems.db.user-settings`
- `rems.db.users`
- `rems.db.workflow`

## Open Questions

- Are assumptions about memory usage correct?
- Should we have read-only caches in e.g. services?
- Should we have a way to do partial updates to read-only caches? (currently we can only invalidate the whole cache)
