# 011: API Key Access Control

Authors: @opqdonut @Macroz @foxlynx

## Background

REMS access control has always been user-based. For example all
application commands must have an actor, and the code checks whether
this actor is allowed to perform this command. API authentication has
been done with two headers: `x-rems-api-key` which must be a valid api
key, and `x-rems-user-id`, which specifies which user is being
impersonated.

Some users have had a need to limit the access available with API
keys. In response, we added a list of allowed roles to each API key.
The roles were a whitelist, with which the roles of the impersonated
user were filtered.

This approach works fine for so-called _explicit roles_ that are
granted using the `role` table in the database. However, we also have
_implicit roles_ that are granted per application, based on the
application state. For example, a user has the `:reviewer` role for an
application if they have been invited to review the application. When
the API key roles were implemented, they were (accidentally?) only
implemented for explicit roles. Extending the implementation for
implicit roles would require more code.

As a concrete example, an API key with only the role `:logged-in`
would still be able to impersonate the handler of an application and
perform handler commands like approving the application.

In order to support the use cases at the end of this document, we need
to either fix the interaction between API key roles & implicit roles,
or choose a new approach.

## Proposed approach

Let's dismantle the API key role support, and instead associate with
each API key:

- an optional whitelist of HTTP paths and methods that the API key is allowed to access
- an optional whitelist of users that the API key is allowed to impersonate

## Use cases

Here are some use cases for API key access control, and a comparison
of the current approach with the proposed approach.

These are from the NKR project, see
[Issue #1910](https://github.com/CSCfi/rems/issues/1910).

### Creating users and applications

Use case: a proxy service creates a user, and and applies for a
resource as the created user.

Current approach: API key with the `:user-owner` and `:applicant`
roles. Additional work would be required to actually support the
`:applicant` role for an API key (it's an implicit role).

Proposed approach: Two API keys:

- One API key for creating users. It can only impersonate a fixed user
  with the `:user-owner` role. (Can also be limited to the
  `/api/users/create` endpoint.)
- Another API key for creating applications. It can impersonate
  anyone, but is limited to the `/api/application/create`,
  `/api/application/save-draft` and `/api/application/submit`
  endpoints.

### Fetching entitlements and applications

Use case: fetching entitlements and applications for any applicant.

Current approach: API key with the `:reporter` role.

Proposed approach: API key associated with a user with the `:reporter`
role. (Additional path restrictions possible if needed.)

### Closing applications

Use case: closing applications (and thus ending entitlements) that are no longer needed.

Current approach: A new `:application-closer` role, and an API key
associated with it. Also needs fixes to the handling of implicit
roles.

Proposed approach: An API key associated with a user that is a handler
for suitable workflows. The API key is limited to the
`/api/applications/close` POST request, and some additional GET
requests as needed.

## Implementation & docs

- [Issue #2127](https://github.com/CSCfi/rems/issues/2127)
- [docs/using-the-api.md](../using-the-api.md)
