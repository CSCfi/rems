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

- POST /add-attachment. Used to add an attachment file to a license.

- POST /remove-attachment. Used to remove an attachment file from a license.

- GET /attachments/[id]. As before, used to get an attachment by id.


Additionally for workflows:

- GET /actors. As before, get the list of available actors.


There are also non-admin API endpoints, which are outside the scope of
this document.
