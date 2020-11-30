# 014: User identities

Authors: @opqdonut @Macroz @jaakkocsc

# Background

TODO

## Problems with the current approach

- The `sub` attribute might be opaque and not usable between systems, e.g. when sending entitlements to other systems
- Different login methods may have different formats for externally referrable fields
- Different login methods might use conflicting values for claims
  - E.g. `foo@csc.fi` via HAKA or CSC SSO (TODO: do we know of cases like this?)
- A single user might have multiple external identifiers (TODO: do we know of cases like this?)

## Use cases for user identities

- Referring to users inside REMS data (e.g. workflow handler)
- Referring to users in API responses (e.g. workflow handler)
  - enriching
- Referring to users in API requests (e.g. set workflow handler, get entitlements for user)
- Referring to users in entitlement POST calls

# Decision: internal user identifiers

We'll add internal random user ids to REMS. This internal user id will
be the key that users are referred to within REMS. The user's external
id will be stored in the attributes JSON blob.

TODO: a good index is needed for performance

## Login

When a user logs in, REMS fetches the user's id token.

1. Find the first attribute from `:oidc-user-attributes` that is set in the id token. Call this attribute-value pair `attr: value`.
2. Find a user from the REMS database that has `attr: value` stored in their user attributes
   - If a user was found, set current session's identity to the internal user id of that user.
   - If a user was not found
     - Create a new internal user id and store `attr: value` in its attributes.
     - Set the current session's identity to the just created internal user id

## User identity resolution

When somebody uses the userid `xyz` in an API call, REMS searches for the user like this:

1. If there is a user with a REMS internal user id `xyz`, use that user
2. Iterate through every attribute `attr` in `:oidc-user-attributes`, in order
   1. If there is a user with `attr: xyz`, use that user
   2. If not, keep iterating
3. If no user was found, return HTTP 400 Bad Request

# Examples

## Referring to users by external id

REMS is configured to use two userid attributes:

```
:oidc-userid-attributes ["cscId" "eppn"]
```

The handler has logged in with a cscId and a user has logged in with
an eppn. This is the `users` database table:

```
userid                           | attributes
---------------------------------+------------------------------------------------------
5e8edd851d2fdfbd7415232c67367cc3 | {"cscId": "handler@csc.fi", "name": "Hannah Handler"}
b58996c504c5638798eb6b511e6f49af | {"eppn": "user@example.com", "name": "Example User"}
```

An API call impersonating the handler adds the user as a reviewer,
referring to the user by their eppn. Note how `x-rems-user-id` must
always be an internal REMS user id. (TODO should we allow external ids?)

```
POST /api/applications/request-review
x-rems-user-id: 5e8edd851d2fdfbd7415232c67367cc3

{"application-id": 1234
 "reviewers": ["user@example.com"]}
```

REMS finds the user and the command succeeds. When the contents of the
application are queried, the JSON document contains internal userids
as well as external ids:

```
GET /api/applications/1234
{"application/id": 1234,
 "application/events":
 [...
  {"event/type": "application.event/review-requested",
   "application/id": 1234,
   "event/actor": "5e8edd851d2fdfbd7415232c67367cc3",
   "event/actor-attributes": {"userid": "5e8edd851d2fdfbd7415232c67367cc3",
                              "cscId" "handler@csc.fi",
                              "name": "Hannah Handler"}
   "application/reviewers": [{"userid": "b58996c504c5638798eb6b511e6f49af",
                              "eppn": "user@example.com",
                              "name": "Example User"}],
   ...}],
 ...}
```

## Changing an identity

Problem: A user has logged in using some identity provider and created
some applications. However now he has started logging in via another
identity provider and the entitlements should be associated with his
new identity.

Solution: Copy the users attributes from their new `users` row to
their old `users` row. Remove the new `users` row. Now when the user
logs in they get their old identity, which the entitlements are
associated with. The external id related to those entitlements has
also changed.

# Implementation plan

## 1. Add support for multiple userid attributes

First off, add support for a list of `:oidc-userid-attribute` values.
This would be an ordered list of attributes to check when the user
logs in. The value of the first attribute that is found gets used as
the userid.

This is backwards compatible with current behaviour.

## 2. Workaround for THL instance

The `sub` attributes are going to change when the identity provider
for the THL instance changes. However for some time the IDP will be
able to provide an `old_sub` attribute for the users that log in via
the old methods (via Haka, eDuuni or Virtu).

For users logging in via the new elixir method, we'll use the
`elixirId` attribute as the user id. This will cause the right user
ids to be used when pushing entitlements to other Elixir services.

This can be accomplished by setting `:oidc-userid-attributes` to `["elixirId" "old_sub"]`.

## 3. Add internal ids

Implement the full internal ids solution described in this ADR in
2021Q1.
