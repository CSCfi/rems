# REMS Glossary

## Core concepts

- resource: an external resource
  - has an identifier that's meaningful outside rems
  - might have an URN
- entitlement: the right of a user to access a resource
  - has a start and an end
  - entitlements are granted when applications are approved

## Applications

- catalogue item: resource+workflow+form
  - internal to rems
- workflow: describes how an application is handled
  - the new dynamic workflow is pretty much just a list of handler user ids
  - the old round-based workflow had a number of rounds, and approvers and reviewers attached to those rounds
- form: a form template that an applicant fills in when applying for a catalogue item
- licenses: the applicant must accept a number of licenses when making an application
  - can be text, a link, or an attached file
  - can currently be attached to workflows, forms or catalogue items
  - upcoming: dynamic licenses added to application by handler
  - https://github.com/CSCfi/rems/issues/914
- application: answers + catalogue item(s) + events
  - answers mean values for the form fields + license acceptance
  - can also contain additional members
  - an application can have multiple catalogue items _if_ they have the same form & workflow

## Users & roles

- handler: in a dynamic workflow, the user who is responsible for steering the application throw the approval process
  - the handler can request comments and decisions from other users
  - finally the handler either approves or rejects the application
- applicant: the user who fills in the application
- member: additional members can be added to an application
  - they will receive entitlements once the application is approved and they have accepted the licenses

## Events

- submitted: the applicant submits the application when he thinks it is ready for approval
- comment requested: the handler has requested that an other user comment on an application
- commented: comments are in response to comment requests
- decision requested: the handler has requested that an other user decide whether to accept or reject this application
- decision: the response to a decision request, that is, accept or reject
  - a decision does not cause the application to be approved or rejected, it is up to the handler to do that
- approved: the handler has approved the application
- rejected: the handler has rejected the application
- returned: the handler has returned the application to the applicant for editing
- closed: the handler has closed an approved application, perhaps because it is obsolete
