# Bots

REMS has a number of features implemented as _bots_, automatic
users, such as handlers that can be attached to a workflow.

To use a bot, create a user with the corresponding user id, and add
that user as a handler to a workflow, if necessary. The 'email' attribute for the
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

## Expirer bot

User id: `expirer-bot`

The Expirer bot is used to remove applications and send notification email to
application members about impending expiration. Expirations can be configured
by application state for both application removal and sending reminder email.

Unlike other bots, the Expirer bot is designed to be triggered by an external
event, such as a periodically ran process. The bot is supplied an application
and it returns appropriate command description, if applicable. Responsibility
of the command execution is left to the caller. A scheduler is implemented in
REMS, enabled by config option `:application-expiration` to periodically call
the Expirer bot. Expirer Bot observes applications' last activity and decides
if application should be deleted or notification emails sent. If the activity
timestamp is updated, users may potentially receive new notification email in
the future.

Depending on the application state and config, the Expirer bot can return one
of two commands:
- `application.command/delete` deletes application immediately, and
- `application.command/send-expiration-notifications` sends emails to members

NB: `:expirer` role must be granted to Expirer bot. This can be done via CLI,
using the command `grant-role`.

NB: currently only draft application removal is supported by REMS.

Example of creating the bot user with the API.

```sh
curl -X POST -H "content-type: application/json" -H "x-rems-api-key: 42" -H "x-rems-user-id: owner" http://localhost:3000/api/users/create --data '{"userid": "expirer-bot", "name": "Expirer Bot", "email": null}'
```

## Bona Fide bot

User id: `bona-fide-bot`

The Bona Fide bot is used for granting peer-verified GA4GH
ResearcherStatus visas. See [ga4gh-visas.md](ga4gh-visas.md) for more
background.

The Bona Fide bot is designed to be used with
- a _default workflow_ that has the bot as a handler (and optionally some human handlers)
- a catalogue item that has a form that has an email field (in case of multiple email fields, the _first one_ is used)
- NB: `invite-decider` command must not be disabled in config

When an application gets submitted for the catalogue item, the bot
sends a _decision request_ to the email address it extracts from the
application. Then the bot waits until the recipient of the request
logs in to rems and performs the _decide_ action. At this point:

- If the decider has a ResearcherStatus visa (with `"by": "so"` or
  `"by": "system"`, see from their IDP, [ga4gh-visas.md](ga4gh-visas.md)):
  - and if the decider posted an approve decision: the bot approves the application
  - and if the decider posted a reject decision: the bot rejects the application
- If the decider doesn't have a ResearcherStatus visa, the bot rejects the application

See [Bona fide pusher for an example use case](../resources/addons/bona-fide-pusher/README.md)
