# 017: Applicant Form UX

Authors: @opqdonut, @Macroz, @marharyta

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

## B) Validation errors are shown late

You need to perform an action (save/submit) to see the validation
errors. Technically we should be able to run the validation code on
the frontend at any point. There's no need to talk to the backend.

**Possible solution**: Validate when user stops typing / leaves field.

Questions:
- What about partially filled in forms? It would be best to not show validation errors for fields that haven't been edited yet.
- What if a user navigates away from the application and back? Do they see the validation errors, or perhaps just the state of the application as-is before they write something.
- What to do with required fields? Should they all turn red immediately or only upon save? Or only upon submit?

Idea: three states for application fields:
- untouched (not filled in at all)
- edited but not valid (can save, can't submit)
- filled in and completely valid (can submit)

## C) You need to save your work explicitly

You need to click on the "Save draft" button to save your work.
Arguably not a problem, users are used to Save buttons?

**Possible solution 1**: Autosave (cache) into browser localstorage We
wouldn't call this caching "saving" to the user, instead their work
would automatically be preserved in their browser, so if they e.g.
navigate away from the application page and navigate back, they'll
still se their work-in-progress application.

**Possible solution 2**: Autosave into REMS db. Also needs problem A to be fixed.
Cons: lots of events generated to the REMS db.

# TODO Decision

Here's a step-by-step plan for fixing these. We might not end up doing all of the steps.

1. (Problem A) Make save succeed even with validation errors. Show validation errors in yellow (instead of red).
2. (Problem B) Run validations in the frontend after the user has stopped typing.
3. TODO
4. TODO
