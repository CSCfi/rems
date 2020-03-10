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
- form: an instance of a form template that an applicant fills in when applying for a catalogue item
- form template: the definition of a form including fields and their relationships
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
- blacklist: a per-resource list of users that may not be given access to the resource. Managed by handlers and owners.

## Users & roles

- handler (of a workflow): in a dynamic workflow, the user who is responsible for steering the applications of
  the workflow through the approval process
  - the handler can request reviews and decisions from other users
  - finally the handler either approves or rejects the application
- applicant (of an application): the user who fills in the application
- member (of an application): additional members can be added to an application by the applicant or the handler
  - members can not edit the application
  - they will receive entitlements once the application is approved and they have accepted the licenses
- reporter: a role that can view all applications
- user: somebody who is logged in to REMS. They can either be an applicant, a handler or somebody who
  responds to requests from handlers
- user-owner: a role that can create & update user details
- owner: a role that can create and edit resources, workflows, catalogue items, etc.
- organization-owner: like owner, but can only create and edit items belonging to the user's organization

## Events

A selection of the events that can happen in REMS

- submitted: the applicant submits the application when he thinks it is ready for approval
- review requested: the handler has requested that an other user review an application
- reviewed: in response to review requests
- decision requested: the handler has requested that an other user decide whether to accept or reject this application
- decision: the response to a decision request, that is, accept or reject
  - a decision does not cause the application to be approved or rejected, it is up to the handler to do that
- remarked: a user involved with the application (e.g. handler, reviewer or decider) has left a comment
- returned: the handler has returned the application to the applicant for editing
- approved: the handler has approved the application
- rejected: the handler has rejected the application
- closed: the handler has closed an approved application, perhaps because it is obsolete
- revoked: the handler has revoked access rights due to misuse and placed applying users on the blacklist

## Architectural concepts

- command: a description of an action a user wants to perform. Commands can result in errors or if successful,
  a number of events. Concretely, a JSON blob POSTed to an endpoint like /api/application/approve
- event: a description of something that has happened. REMS stores
  events in the database instead of storing the current state. This approach is called _Event Sourcing_.
- process manager: something that reacts to new events with either
  side effects (e.g. sending email) or more new events (e.g. approver
  bot)
- bot: a special kind of process manager that performs activities that
  a normal user might perform. For example, the approver and rejecter
  bots.
- outbox: a database table that tracks side-effects that should happen
  and their retries. For example emails or entitlement POSTs.
