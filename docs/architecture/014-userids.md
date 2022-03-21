# 014: User identities

Authors: @opqdonut @Macroz @jaakkocsc

# Background

Currently REMS uses the userid received from the OIDC server (from the
`sub` claim or another claim based on configuration), and uses this
userid as the internal database key for that user. The userid is
embedded in application events, workflows, etc.

## Problems with the current approach

- The `sub` attribute might be opaque and not usable between systems, e.g. when sending entitlements to other systems
- Different login methods may have different formats for external user identifiers
- Different login methods might use conflicting values for claims
  - E.g. `foo@csc.fi` via HAKA or CSC SSO (TODO: do we know of cases like this?)
- A single user might have multiple external identifiers (TODO: do we know of cases like this?)

## Use cases for user identities

A user identifier can be used for multiple things. We might want to
use different identifiers for different purposes.

- Referring to users inside REMS data (e.g. workflow handler)
- Referring to users in API responses (e.g. workflow handler)
  - enriching
- Referring to users in API requests (e.g. set workflow handler, get entitlements for user)
- Referring to users in entitlement POST calls

# Decision: internal user identifiers

We'll add internal random user ids to REMS. This internal user id will
be the key that users are referred to within REMS. The user's external
id will be stored in the attributes JSON blob.

This will allow us more flexibility in the future when identity
requirements and use cases change, and might also make all sorts of
migrations easier (since internal user ids don't need to be touched).

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
3. If no user was found, or multiple users were found, return HTTP 400 Bad Request

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
always be an internal REMS user id (but see Open questions).

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

[Issue #2366](https://github.com/CSCfi/rems/issues/2366),
[PR #2471](https://github.com/CSCfi/rems/pull/2471)

## 2. Workaround for THL instance

The `sub` attributes are going to change when the identity provider
for the THL instance changes. However for some time the IDP will be
able to provide an `old_sub` attribute for the users that log in via
the old methods (via Haka, eDuuni or Virtu).

For users logging in via the new elixir method, we'll use the
`elixirId` attribute as the user id. This will cause the right user
ids to be used when pushing entitlements to other Elixir services.

This can be accomplished by setting `:oidc-userid-attributes` to `["elixirId" "old_sub"]`.

## 3. Use internal ids everywhere

Implement the "Login" section of this ADR, and also write the
necessary database migrations (see also Migration under Open questions
below).

[Issue #2771](https://github.com/CSCfi/rems/issues/2771)

## 4. Support for external ids in APIs

Implement the "User identity resolution" section of this ADR, one API
at a time. This should probably be in the same release as step 3.

[Issue #2772](https://github.com/CSCfi/rems/issues/2772)

# Open questions

## API format

Should we switch all APIs that take userids to a structured
`{"userid": "abc123"}` form? Then we could specify users more
explicitly like `{"eppn": "user@example.com"}`.

Alternatively, we could make the APIs polymorphic so that you can
specify a user as `"userid"` or `{"attr": "value"}`. This would be
backwards-compatible but make the code more complex.

## `x-rems-user-id`

Should we allow external user ids in `x-rems-user-id`?

## User search API

Should we have an API for searching / resolving users?

[Issue #2773](https://github.com/CSCfi/rems/issues/2773)

## Migration

Should we migrate existing userids to new random values or keep them
as-is?

## Database indexes

A good index is needed for performance when looking up users based on
attributes. Is storing the data in JSONB fine or should we also add
new columns?

We can also use Lucene to implement user searching.

[Issue #2774](https://github.com/CSCfi/rems/issues/2774)

## Push / post etc. outbound APIs

See issue https://github.com/CSCfi/rems/issues/2888.

## Notes

2022-01-24 We discussed whether we should store or show more attributes of the users. For example, should the handler see the identities of the user. The old attributes are always replaced when the user comes again so changing IdP also may change the email (or other attributes), which could confuse the handler. We decided not to do anything about this yet, since this is existing behavior. Also storing old attributes might mean that we store and show a user's old email or name and there is no way to change them if the authentication method is removed.
