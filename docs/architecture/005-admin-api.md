# 005: admin api

Authors: @Macroz @okahilak @opqdonut

# Terms

Item: Catalogue item, resource, form, workflow, or license.

# Background

Different items have the enabling/disabling and archiving/unarchiving
operations in common. Previously, these operations have been done
via the API using update endpoint, e.g., /api/workflow/update, which
updates both enabled and archived status at once.

# Current status

Lately, we have added editing the content of an item for certain types
of items (namely, workflow, catalogue item, and, in some cases, form).
Editing is done using the update endpoint, i.e., /api/workflow/update,
in the case of workflows, and a new edit endpoint, /api/form/edit, in
the case of forms and catalogue items.

The current situation has two problems:

- The API is not consistent across different types of items,

- The terms update and edit are easily confused.

# The decision

For each type of item, let's have the following API endpoints.

Each is prefixed by /api/[type], where type is one of the following:
catalogue-item, resource, form, workflow, license.

- GET /. As before, used to get all items.

- GET /[id]. As before, used to get a single item by id.

- POST /create. As before, used to create a new item.

- PUT /edit. Used to change content of an item.

- PUT /archived. Used to archive/unarchive an item, e.g., PUT /archived false.

- PUT /enabled. Used to enable/disable an item, e.g., PUT /enabled true.


Additionally for forms:

- GET /[id]/editable. As before, used to get the 'editable' status of an item.


Additionally for licenses:

- POST /add_attachment. As before, used to add an attachment file to a license.

- POST /remove_attachment. As before, used to remove an attachment file from
  a license.

- GET /attachments/[id]. As before, used to get an attachment by id.


Additionally for workflows:

- GET /actors. As before, get the list of available actors.


The API additionally consists of (not prefixed):

- GET /api/catalogue/. As before, get the catalogue of items for the UI.

- GET /api/entitlements/. As before, get entitlements.

- GET /api/user-settings/. As before, get user settings.

- PUT /api/user-settings/. As before, update user settings.


The API endpoints related to applications:

- GET /api/my-applications/. As before, get the user's own applications.

- GET /api/applications/. As before, get all applications.

- GET /api/applications/[id]. As before, get an application by id.

- POST /api/applications/create. As before, create a new application.

- POST /api/applications/copy-as-new. As before, create a new application
  as a copy of an existing application.

- GET /api/applications/todo. As before, get all applications that the user
  needs to act on.

- GET /api/applications/handled. As before, get all handled applications.

- GET /api/applications/commenters. As before, get available third party
  commenters.

- GET /api/applications/members. As before, get REMS users available for
  application membership.

- GET /api/applications/deciders. As before, get available deciders.

- GET /api/applications/attachment/[id]. As before, get an attachment by id.

- POST /api/applications/add-attachment/. As before, add an attachment.

- POST /api/applications/accept-invitation/. As before, accept an invitation.
