# 021: Upgrading items

Status: Draft 
Authors: @Macroz

## Problem

A catalogue item, workflow or form may need to change to a new version 
while an application is in draft state, or in handling.
This may affect handling because new questions may be introduced, or old ones removed.

It should be straightforward to update a form and have it used in new applications. 
Likewise in the case of workflow worms the workflow of a catalogue item where the workflow
may change. The same problem exists for licenses as well.

We don't want to lose the answers from the user, and if it is reasonable, 
we want to allow applications to continue into handling with minimal changes.

## Assumptions
- It is good for the applicant to know that the application is going to be upgraded.
And they can do any necessary changes or additions in their answers.
- It's not compulsory to use the upgrade feature, i.e. it's OK to keep accepting old applications.
The owner can choose when to disable the old catalogue items.
If the form, or its upgrade, has the same field id, it is the same question, even if the wording is different.
- A form is an upgrade to another form if it has been marked so.

## Solution
- In the form editor, add a field to set that this form has been upgraded into another form.
Then we can track whether the answers can be copied or not.
- In the catalogue item editor, add a field to set that this catalogue item has been upgraded.
Then we can programmatically upgrade applications too.
- When upgrading an application, the answers are copied from old forms to new forms.
The semantic meaning of a question shall not change if the field id is the same.

## Open Questions
- When are applications upgraded?
  - When a catalogue item is marked as an upgrade and enabled?
  - When the applicant chooses to upgrade?
  - When the handler wants the application to upgrade?
- Should catalogue item enable/disable mean also that all in-progress applications must upgrade?
Or should there be an option to force upgrade of in-progress applications?
