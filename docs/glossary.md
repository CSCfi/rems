---
---

# REMS Glossary

## Core concepts

- REMS: Resource Entitlement Management System
- resource: an external resource
  - has an identifier that's meaningful outside rems
  - might have an URN
- entitlement: the right of a user to access a resource
  - has a start and an end
  - entitlements are granted when applications are approved

## Applications

- workflow: describes how an application is handled
  - the dynamic workflow is pretty much just a list of handler user ids
- form: a form template that an applicant fills in when applying for a catalogue item
- licenses: the applicant must accept a number of licenses when making an application
  - can be text, a link, or an attached file
  - can currently be attached to workflows, forms or catalogue items
  - upcoming: dynamic licenses added to application by handler
- catalogue item: something an applicant can pick from the catalogue and apply for
  - catalogue item = resource + workflow + form
  - internal to rems
- catalogue: the list of all (enabled) catalogue items
- application: an instance of an applicant applying for entitlement to some resources
  - application = answers + catalogue item(s) + events
  - answers mean values for the form fields + license acceptance
  - can also contain additional members
  - an application can have multiple catalogue items _if_ they have the same form & workflow

## Users & roles

- handler (of a workflow): in a dynamic workflow, the user who is responsible for steering the applications of
  the workflow through the approval process
  - the handler can request reviews and decisions from other users
  - finally the handler either approves or rejects the application
- applicant (of an application): the user who fills in the application
- member (of an application): additional members can be added to an application by the applicant or the handler
  - members can not edit the application
  - they will receive entitlements once the application is approved and they have accepted the licenses
- user: somebody who is logged in to REMS. They can either be an applicant, a handler or somebody who
  responds to requests from handlers
- reporter: a role that can view all applications
- owner: a role that can create and edit resources, workflows, catalogue items, etc.

## Events

- submitted: the applicant submits the application when he thinks it is ready for approval
- review requested: the handler has requested that an other user review an application
- reviewed: in response to review requests
- decision requested: the handler has requested that an other user decide whether to accept or reject this application
- decision: the response to a decision request, that is, accept or reject
  - a decision does not cause the application to be approved or rejected, it is up to the handler to do that
- returned: the handler has returned the application to the applicant for editing
- approved: the handler has approved the application
- rejected: the handler has rejected the application
- closed: the handler has closed an approved application, perhaps because it is obsolete
