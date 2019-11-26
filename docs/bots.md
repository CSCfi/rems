# Bots

REMS has a number of features implemented as _bots_, automatic
handlers that can be attached to a workflow.

To use a bot, create a user with the corresponding user id, and add
that user as a handler to a workflow. The 'email' attribute for the
user can be null, or the attribute can be left out completely.

## Approver Bot

User id: `approver-bot`

The approver bot approves applications automatically, unless a member
of the application is blacklisted for an applied resource.

If the applicant or any of the members has been blacklisted for an
applied resource, the bot will do nothing and a human will need to
handle the application.

## Rejecter bot

User id: `rejecter-bot`

The rejecter bot complements the approver bot. It rejects applications
where a member is blacklisted for an applied resource.
