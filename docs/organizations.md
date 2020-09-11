# Organizations

REMS supports labeling catalogue items, forms, resources, workflows
and licenses with an **organization**.

The various features relating to organizations are still work in
progress, but here is a summary of the current status.

## Available organizations

Available organizations are stored in the database table `organization`.
While migrating or taking a new REMS instance into use, you should create them
either using the API or administration UI as you are creating licenses, forms etc.
Licenses, forms etc. all require you to specify the organization so you
must create at least one.

## Organizations of users

Users' organizations are based on the `:organizations` attribute from
the identity provider. There can be one or more organizations at the same time.
At the moment this is not used for anything but it is shown to the handler of
an application.

## The organization owner role

The `organization-owner` role can only create and edit things that
belong to his own organization. The `owner` role can create things
that belong to any (available) organization. The ownership for organizations
is managed through the `organization` table and `:organization/owners` key.
You can use the administration UI to assign owners.
