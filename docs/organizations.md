# Organizations

REMS supports labeling catalogue items, forms, resources, workflows
and licenses with an _organization_.

The various features relating to organizations are still work in
progress, but here is a summary of the current status.

## Available organizations

Available organizations are configured using the `:organizations`
config key. If it is not present, the default is one organization,
named "default".

## Organizations of users

Users' organizations are based on the `:organization` attribute from
the identity provider

## Restrictions on visibility

Users without the `owner` or `handler` roles won't see items outside
their own organization in the administration pages. In practice, this
means users with only the `organization-owner` role.

## Restrictions on creating things

The `organization-owner` role can only create things that belong to
his own organization. The `owner` role can create things that belong
to any (available) organization.

The administration UI lets users only combine things that have the
same organization. This means:
- resource can only contain licenses with the same organization
- catalogue item can only contain resource, form and workflow that are
  within the same organization

These constraints are not currently enforced in the backend, only in
the UI.
