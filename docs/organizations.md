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

## The organization owner role

The `organization-owner` role can only create and edit things that
belong to his own organization. The `owner` role can create things
that belong to any (available) organization.
