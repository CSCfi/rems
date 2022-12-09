# 012: Layers

Authors: @opqdonut @Macroz

This ADR tries to document the current state of the REMS application
architecture for the backend.

## Layers

```

HTTP               /api/licenses/*      /api/workflows/*     /api/user-settings/*                   ...


                   +----------+         +----------+        +---------------+
API layer          | license  |         | workflow |        | user-settings |                       ...
                   +----------+         +----------+        +---------------+
                        |                    |                      |        \
                        |                    |                      |         '------+
                        |                    |                      |                |
                        v                    v                      v                v
                   +----------+         +----------+        +---------------+     +-----+
Service layer      | license  |         | workflow |        | user-settings |     | ega |           ...
                   +----------+         +----------+        +---------------+     +-----+
                        |      \             |                      |                |
                        |       '--------+   |                      |                |
                        |                |   |                      |                |
                        v                v   v                      v                v
                   +----------+         +----------+        +---------------+     +-----+
DB/EXT layer       | license  |         | workflow |        | user-settings |     | ega |           ...
                   +----------+         +----------+        +---------------+     +-----+
                             |           |                   |
                             |           | +----------------'
                             v           v v
                            +--------------+
                            | rems.db.core |
                            +--------------+
                                   |
                                   v
                                .----.
                               /      \
                              |\      /|
                              | '----' |
                              |postgres|
                              |        |
                               \      /
                                '----'
```

### The API Layer

The API layer takes in a HTTP request and transforms it into ideally
one call of the service layer. The API layer is also responsible for
- coarse-grained access control (API `:roles`)
- API schemas
- swagger documentation

The API layer lives in the `rems.api.` namespaces.

### The Service Layer

Each service represents the public API for a specific part of REMS that is easy to call
so it must handle any dependencies etc:
- creating a resource, workflow etc.
- fetching a complete representation of a resource, workflow etc.
- business actions like enabling a resource, workflow etc.
- joining related items like the organization of a resource, ...
- process manager and async actions of a command

A service will typically call multiple different DB layer namespaces
to implement the functionality.

The Service layer lives in the `rems.service.*` namespaces.

Historical note: the service layer was created in PR #1487 (and
expanded subsequent PRs like #1490 #1491 #1495 #1854) to avoid
circular dependencies between db namespaces. That PR contained some
discussion about what the purpose of the service namespaces should be,
but no clear conclusion was reached.

### The DB Layer

The purpose of the DB layer is to handle serializing and deserializing
data into the database. This can involve schema coersions (see
`rems.db.events`) or renaming keys (see `rems.db.form`). A single
namespace in the DB layer should handle one concept. The DB layer can
also contain simple domain logic (for example `rems.db.attachments`
has checking for allowed attachment types). Namespaces in the DB layer
should not depend on each other.

The actual SQL queries are defined in `resources/sql/queries.sql`, and
each one automatically gets a corresponding function in the
`rems.db.core` namespace. Other `rems.db.*` namespaces should depend
on `rems.db.core` to perform the queries they need.

## Problems

### Circular dependencies

What to do about circular dependencies between services?

For example after #2261, `rems.api.blacklist` calls
`rems.service.command` and `rems.service.blacklist`. This is
because `services.command` already calls `services.blacklist`, so we
can't have `services.blacklist` call `services.command`.

NB: For the data dependencies we have `rems.service.dependencies`.

### Namespaces that don't obey these rules

`rems.db.applications` depends on many other DB namespaces. A service NS should probably be split out.

`rems.application.rejecter-bot` and `rems.application.approver-bot`
should probably be services since they invoke DB functions.

TODO: are there others?

## Other code

Notable pieces of code that don't fit into the API-Services-DB layering (and don't need to):

- The application model in `rems.application.*` is pure code that
  implements the domain model. This can be safely called from
  anywhere.
- The `rems.common.*` namespaces are code shared by the UI and
  backend. See for example `rems.common.form` which contains form
  template validation.
- Various utility and configuration namespaces like `rems.api.util`,
  `rems.config` and `rems.context`
- Database migrations (`rems.migrations.*`)
- Supporting API implementation like `rems.auth.*`, `rems.middleware`
  etc.

## Amendment: Schemas

Date: 2021-02-15
Authors: @opqdonut @Macroz

This layering should also apply to schemas. We want to make it
possible to separate DB schemas from API schemas, and make it apparent
when they are shared.

In particular:

- `rems.api.schema` should only be used from `rems.api.*` namespaces. It should be used for common schema fragments that many APIs share. Also big or important schemas can live in `rems.api.schema` for discoverability.
- `rems.api.*` namespaces that define APIs can also contain schemas for those APIs, if the schemas don't need to be shared.
- `rems.schema-base` contains common schemas that can be used everywhere.
- `rems.schema-base` and `rems.api.schema` shouldn't depend on any api, db or service components.
- Schemas used for JSON in the DB should be in `rems.schema-base` or the `rems.db.*` namespace for that feature.

## Amendment: External layer for integrations

Date: 2021-06-08
Authors: @Macroz

Sometimes there are external services other than the database. We can have namespaces like `rems.ext.*` for these, like `rems.ext.ega`.
These should also be referred to from a service namespace like the `rems.db.*` namespaces are.
