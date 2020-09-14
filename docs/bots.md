# Bots

REMS has a number of features implemented as _bots_, automatic
handlers that can be attached to a workflow.

To use a bot, create a user with the corresponding user id, and add
that user as a handler to a workflow. The 'email' attribute for the
user can be null, or the attribute can be left out completely. The
name of the bot will be visible to the applicants in the event log
as the person who approved or rejected their application so make it
something sensible!

## Approver Bot

User id: `approver-bot`

The approver bot approves applications automatically, unless a member
of the application is blacklisted for an applied resource.

If the applicant or any of the members has been blacklisted for an
applied resource, the bot will do nothing and a human will need to
handle the application.

Example of creating the bot user with the API.

```sh
curl -X POST -H "content-type: application/json" -H "x-rems-api-key: 42" -H "x-rems-user-id: owner" http://localhost:3000/api/users/create --data '{"userid": "approver-bot", "name": "Approver Bot", "email": null}'
```

## Rejecter bot

User id: `rejecter-bot`

The rejecter bot complements the approver bot. It rejects applications
where a member is blacklisted for an applied resource. This can happen
in three ways:

1. An applicant who is already blacklisted for a resource submits a
   new application for that resource. Rejecter bot rejects the
   application.
2. An approved application is revoked. This adds the applicant and the
   members to the blacklist for the applied resources. Rejecter bot
   then rejects any open applications these users have for the
   resources in question.
3. A user is added to a resource blacklist manually (via the
   administration UI or the API). Rejecter bot then rejects any open
   applications this user has for the resource in question.

NB! The rejecter bot can only reject applications for which it is a
handler.

Example of creating the bot user with the API.

```sh
curl -X POST -H "content-type: application/json" -H "x-rems-api-key: 42" -H "x-rems-user-id: owner" http://localhost:3000/api/users/create --data '{"userid": "rejecter-bot", "name": "Rejecter Bot", "email": null}'
```
