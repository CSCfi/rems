# 009: Review and form model

Authors: @macroz

## Background

In the existing handling process, the reviewer has been thought of as having the same viewing rights as the handler. It has been assumed that all the people that need to take part in the handling can and should see the whole application. However, now we have a new need to limit the visibility of fields to reviewers.

First step we took was to include per field privacy state: `public` or `private`. Public fields are shown to all the people who take part in handling, including reviewers. Private fields are not sent to reviewers and are meant for the handler and decider only.

While this solves the immediate problem of not letting everyone know everything, ideally some fields could be public for some reviewer and private for another. Now the information from a private field must be copy-pasted to the review request comment or shared otherwise.

## Options

There exist at least five possibilities to solve this.

1. require the handler to copy-paste the relevant data from private fields to each reviewer (status quo)
2. model each reviewer type as a catalogue item with extra fields, review request is sent with certain catalogue item selected and it controls which fields are shown to the reviewer
3. model each reviewer type as a catalogue item with separate form, change application model to combine multiple forms into one application, review request is sent with certain catalogue items selected and it controls which fields are shown to the reviewer
4. model each reviewer type as a catalogue item with same form, model private fields as repeating fields that repeat per catalogue item, review request is sent with certain catalogue items selected and it controls which fields are shown to the reviewer
5. add a field to the model where you can add multiple answers, model private fields as repeating fields that repeat per each answer to the previous field, review request is sent with certain answer selected and it controls which fields are shown to the reviewer

## Discussion

As this is deemed most important feature to improve, we can disregard **option 1** doing nothing.

Modeling fields into catalogue items does not seem like a sensible direction, it would introduce exceptions. We should therefore disregard **option 2** and prefer the others e.g. **option 3** modeling the fields into a form.

Current application bundling logic bundles all catalogue items to a single application if they share the form and workflow. It seems like a natural extension of the model to allow several forms to be included and combined.

A naive version of form merging just duplicates all the fields in the form and a smart version merges similar fields so the applicant has to answer only once each. However we don't separately model each field so it's difficult to identify which field is exactly the same and can be merged. One would have to make sure each field has a globally distinctive id. We know that there would be around 1000 potential forms that would have to be made with shared and separate parts. Maintaining the forms and their shared parts would be too much work and would need an extension of the editor as well.

A possibile variant of form merging is also forcing shared parts to be in a separate catalogue item and form. Then there are a lot of forms but fields are not duplicated. However the applicant must somehow always choose wisely and include the shared catalogue item.

All in all while multiple forms, and **option 3** are a sensible extension to our model, it certainly requires large changes to the logic all around the codebase.

In the **option 4** we would make a new repeating functionality that would repeat per applied catalogue item. Modeling each reviewer type as a catalogue item seems like a business decision, not something for us. It requires the organization to either be able to statically know each option, or have a process for adding missing ones. While the repeating logic is sound, we can also consider **option 5** as the same, but not having the need to couple this into catalogue items. So lets prefer **option 5** and not this one.

A field type where you can "press add button" to add more rows sounds also like a natural extension to the types of fields we have. Also repeating fields based on the count of a previous answer sounds sensible. Some kind of grouping logic may be needed to repeat fields in a group (ABCABCABC and not AAABBBCCC). Therefore **option 5** seems like a sensible extension that we should try.

Review request must in practice be changed in all of these. This is due to the fact that receiving organizations can't easily be modeled, they can be email inboxes. Therefore there should be an option to select the organization type i.e. which private fields are shown to them. This would be part of all the solutions, either picking from a catalogue item / resource or answer to a multiple answer field.

It is likely that not all reviwers have existing REMS user accounts, and certainly it's not possible to select which one because of the email group box requirement. Therefore inviting a reviewer to review, and connecting the person to the application, must be done like in the invite member function.

## Decision

Lets try option 5 and if it doesn't work out go with 3.

The tasks should be
1. reviewer invitation
  - when doing a request, have separate email receiver box
  - send email with token
  - connect reviewer to correct role upon logging in and accepting token
  - details like in member invitation
2. review request
   - should have a field for selecting which private answers to send i.e. the reviewer type
   - autocomplete with at least the special repeating field types as options
   - each reviewer can have a type set
   - form fields are shown based on detailed type
3. repeating field type
   - adding rows
   - repeating all fields of the same repeater for each answer
