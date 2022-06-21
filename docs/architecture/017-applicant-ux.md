# 017: Applicant Form UX

Authors: @opqdonut, @Macroz, @marharyta, @aatkin

This ADR aims to collect together some topics like validations in
frontend, autosaving, etc. that are related to the Applicant's user
experience. There have been previous discussions about all of these
topics but no unified presentation.

Based on ticket [#2639](https://github.com/CSCfi/rems/issues/2639)

# Problems with the current applicant UX

## A) Validation errors block saving

You don't know if your inputs (e.g. ip address) are valid before
pressing Save, and then you need to fix all of these errors before
REMS saves your work.

This can be irritating if you're working on a long application, and
just want to save your work and go home. It's less of an issue if
users save early & often.

**Possible solution**: Show validation errors to the user, but perform
the save anyway. Show errors in yellow?

Historical note: historically (in the pre-SPA days) REMS used to work
like this. This topic was more recently touched on in
[#2117](https://github.com/CSCfi/rems/issues/2117). Interestingly
enough, the idea idea seems to have been to only perform schema-style
validations on drafts. However, the email content validation was
included in the spec, so subsequent field types added their content
validations to save-draft (see e.g.
[#2567](https://github.com/CSCfi/rems/pull/2567),
[#2594](https://github.com/CSCfi/rems/pull/2594))

Idea: Think of REMS validations more like spellchecking and less like
validations. Higlight in red things that the applicant will want to
fix, but don't prevent saving.

### 2022-04-06 Save succeeds even with validation errors

Draft saving succeeds again even with validation errors. This is implemented in the PR #2766. Validation errors are now shown in yellow.

## B) Validation errors are shown late

You need to perform an action (save/submit) to see the validation
errors. Technically we should be able to run the validation code on
the frontend at any point. There's no need to talk to the backend.

**Possible solution**: Validate when user stops typing / leaves field.

Questions:
- What about partially filled in forms? It would be best to not show validation errors for fields that haven't been edited yet.
- What if a user navigates away from the application and back? Do they see the validation errors, or perhaps just the state of the application as-is before they write something.
- What if a user starts filling in field 3? Do they see validation errors for fields 1-3 or only 3?
- What to do with required fields? Should they all turn red immediately or only upon save? Or only upon submit?

Idea: Three states for application fields:
- untouched (not filled in at all)
- edited but not valid (can save, can't submit)
- filled in and completely valid (can submit)

Idea: Only show the validation errors for the fields the user actively changes in that "session". Nothing validated when the page is opened.

## C) You need to save your work explicitly

You need to click on the "Save draft" button to save your work.
Arguably not a problem, users are used to Save buttons? What if REMS
automatically saved your work (a la Google Docs)?

**Possible solution 1**: Autosave (cache) into browser localstorage We
wouldn't call this caching "saving" to the user, instead their work
would automatically be preserved in their browser, so if they e.g.
navigate away from the application page and navigate back, they'll
still see their work-in-progress application.

**Possible solution 2**: Autosave into REMS db. Also needs problem A
to be fixed. Cons: lots of events generated to the REMS db. Could work
around this by implementing draft saving (or perhaps just auto-saving)
separately from the application events.

Note: in both of these cases we would also retain the current Save
button.

### 2021-06-18 Consecutive save compaction

The PR #2966 implements consecutive save compaction, making consecutive saves in one event.
The last save event is updated, with a new save event including id and data.
Process managers and notifications are sent as before.

# 2021-11-02 Decision

Here's a step-by-step plan for fixing these.

1. (Problem A) Make save succeed even with validation errors. Show validation errors in yellow (instead of red). [#2766](https://github.com/CSCfi/rems/issues/2766)
2. (Problem B) Run validations in the frontend after the user has stopped typing. [#2614](https://github.com/CSCfi/rems/issues/2614)
3. (Problem B) Autosave in the background by replacing/patching the latest draft-saved event. If the latest event is not a draft-saved then create a new one. [#2767](https://github.com/CSCfi/rems/issues/2767)
