# 013: Command & event design

Authors: @opqdonut @Macroz

This ADR outlines some discussions about event design that were had while implementing
[#2040 Reviewer/decider invitation](https://github.com/CSCfi/rems/issues/2040).

The point of the feature is being able to invite reviewers and
deciders by email. This is similar to the existing feature of inviting
application members by email.

## Existing commands & events

- Command: invite-member
  - Produces event: member-invited
- Command: accept-invitation
  - Produces: member-joined
- Command: request-review
  - Produces: review-requested
- Command: request-decision
  - Produces: decision-requested

### Design 1

First implementation. Trying to share decider and reviewer invitation
logic using a generic invite-actor command. Reuses the existing
accept-invitation command to reuse some frontend & backend routes.
Commit 2049a3651 in [PR #2415][2415].

- Command: invite-actor with `:role :decider` or `:role :reviewer`
  - Produces: actor-invited
- Command: accept-invitation
  - Produces: actor-joined with `:role :decider` or `:role :reviewer`
  - (Or member-joined event if the invitation was for a member)

Problems:

- Naming is hard: actor is an already overloaded term (means the author of an event)
- Actor-joined event needed to duplicate behaviour of existing review-requested event

[2415]: https://github.com/CSCfi/rems/pull/2415

### Design 2

Trying to reuse even more code by reusing the review-requested event. Commit 33c3c79 in [PR #2415][2415].

- Command: invite-actor with `:role :decider` or `:role :reviewer`
  - Produces: actor-invited
- Command: accept-invitation
  - Produces: actor-joined with `:role :decider` or `:role :reviewer`
  - Produces: review-requested OR decision-requested

Benefits:

- Nice split of responsibilities between events
  - actor-joined marks invitation as used
  - existing review-requested event used for granting reviewer rights

Problems:

- After accepting the invitation, the review-requested event triggers a new, potentially misleading notification email to the reviewer
- Event log is also a bit confusing:
  1. "Alice invited Bob to review"
  2. "Bob accepted the invitation to review"
  3. "Alice requested a review from Bob"

Possible solutions:

- Reword log message to be more passive, "Bob is now a reviewer"
- Add some sort of `:notify false` option to the review-requested event to prevent emails (and possibly hide from event list)
- Go back to design 1

### Design 3

After discussions ended up with less code sharing but more explicit API & event structure. Commit 253b66d2e in [PR #2415][2415].

- Command: invite-reviewer
  - Produces: reviewer-invited
- Command: invite-decider
  - Produces: decider-invited
- Command: accept-invitation
   - Produces: reviewer-joined OR decider-joined

Benefits:

- More consistent API: request-review, invite-reviewer; request-decision, invite-decider
- Not reusing {request,decision}-requested events avoids pitfalls in event log & emails
- Reusing the accept-invitation command makes sense because we don't want multiple /accept-invitation URLs

Problems:

- Some code duplication
- Bots & external consumers interested in review requests now need to listen to two events instead of one

## Discussion

Since REMS commands are part of our public API, it makes sense to keep
commands large (that is, one command does a lot of things). This way
the user's intent can usually be represented with one command, keeping
the frontend simple. Also, since one command means one database
transaction (see [ADR 010: Database
transactions](010-transactions.md)), issuing a single command is safer
than issuing multiple commands. API usage is also nicer when you can
often just post a single command.

However, since commands and events are decoupled, we could have these
commands produce multiple small events. So far REMS has favoured most
commands producing just one nongeneric and large event (that is, an
event that has lots of effects). Also commands haven't reused events
(for example review-requested and decision-requested are separate
events). This way the events are more explicit and mirror the user's
intent just like our commands.

Design 1 was an attempt at using smaller, more decoupled events.
However that immediately ran into problems with consuming events
internally: both the event log & email generation would have needed
work.

Perhaps it is best for now to stick to large events so that we can
easily react to the users intent in other code, instead of trying to
recombine smaller events to reproduce intent (e.g. not sending
redundant email reminders)

However, the decision to show the internal event log to users as-is
might make our life harder in the future. We might need to re-evaluate
this later.
