# 009: Review and form model

Authors: @macroz

## Background

In the existing handling process, the reviewer has been thought of as having the same viewing rights as the handler. It has been assumed that all the people that need to take part in the handling can and should see the whole application. However, now we have a new need to limit the visibility of fields to reviewers.

First step we took was to include per field privacy state: `public` or `private`. Public fields are shown to all the people who take part in handling, including reviewers. Private fields are not sent to reviewers and are meant for the handler and decider only.

While this solves the immediate problem of not letting everyone see everything, ideally some fields could be public for some reviewer and private for another. Now the information from a private field must be copy-pasted to the review request comment or shared otherwise.

## Options

There exist at least six possibilities to solve this.

The reviewer is part of some reviewer organization and receives a review request from the handler.

Repeating fields would be a new feature, a field that repeats per some criteria. Either the user can add rows themselves, or a field is repeated once per reviewing organization.

1. Require the handler to copy-paste the relevant data from private fields to each reviewer in a review request (status quo).
   Application contains form fields of one form.
2. Model each reviewer organization as a catalogue item with extra fields.
   Application contains form fields of one form and all extra fields from all catalogue items.
   Review request is sent with certain catalogue items selected and it controls which fields are shown to the reviewer.
3. Model each reviewer organization as a catalogue item with a separate form.
   Application contains form fields from all forms of all catalogue items.
   Review request is sent with certain catalogue items selected and it controls which fields are shown to the reviewer.
4. Model each reviewer organization as a catalogue item with the same form.
   Model private fields, which need a separate answer for each reviewer organization, as repeating fields that are repeated once per catalogue item in the application.
   Application contains form fields of one form.
   Review request is sent with certain catalogue items selected and it controls which (repeated) fields are shown to the reviewer.
5. Add a field type to the model where you can add multiple answers. E.g. click plus to add row.
   Model private fields, which need a separate answer for each reviewer organization, as repeating fields that are repeated once per each answer to the multiple answer field.
   Application contains form fields of one form.
   Review request is sent with certain repeated field answers selected and it controls which fields are shown to the reviewer.
6. Model each reviewer organization as a REMS organization with a separate catalogue item with a separate form.
   Model common fields as a form that can be included in the workflow.
   Application contains form fields from all forms of all catalogue items and the forms of one workflow.
   Review request is sent with certain reviewer organizations selected and it controls which fields are shown to the reviewer.
   Application contains forms and fields from catalogue items and workflows.

## Discussion

As this is deemed most important feature to improve, we can disregard **option 1** doing nothing.

Modeling fields into catalogue items does not seem like a sensible direction, it would introduce exceptions. We should therefore disregard **option 2** and prefer the others e.g. **option 3** modeling the fields into a form.

Current application bundling logic bundles all catalogue items to a single application if they share the form and workflow. It seems like a natural extension of the model to allow several forms to be included and merged.

A naive version of form merging just duplicates all the fields in the form and a smart version merges similar fields so the applicant has to answer only once each. However we don't separately model each field so it's difficult to identify which field is exactly the same and can be merged. One would have to make sure each field has a globally distinctive id. We know that there would be around 1000 potential forms that would have to be made with shared and separate parts. Maintaining the forms and their shared parts would be too much work and would need an extension of the editor as well.

A possible variant of form merging is also forcing shared parts to be in a separate form. Then there are a lot of forms but fields are not duplicated. However the applicant must somehow always choose wisely and include the shared catalogue item. This can be fixed with *option 6* as shown later. Another possibility is including several forms in each catalogue item, one for common and one for the private parts. However there is still the maintenance problem of catalogue items as well as now catalogue items may have to contain forms of several organizations (i.e. application handler and reviewer).

All in all while multiple forms, and **option 3** are a sensible extension to our model, it certainly requires large changes to the logic all around the codebase.

In the **option 4** we would make a new repeating functionality that would repeat an answer field per applied catalogue item. Modeling each reviewer organization as a catalogue item seems like a business decision, not something for us to decide. It requires the organization to either be able to statically know each option, or have a process for adding missing ones. While the repeating logic is sound, we can also consider **option 5** as the same, but not having the need to couple this into catalogue items. So lets prefer **option 5** and not this one.

A field type where you can "press add row button" to add more rows sounds also like a natural extension to the types of fields we have. Also repeating fields based on the count of a previous answer sounds sensible. Some kind of grouping logic may be needed to repeat fields in a group (ABCABCABC and not AAABBBCCC). Therefore **option 5** seems like a sensible extension that we should consider.

The review request must in practice be changed in all of these. This is due to the fact that reviewing organizations can't easily be modeled statically, they can be email inboxes. Therefore there should be an option to select the organization type when sending the review request. I.e. it must be specified per request which private fields are shown to the user who arrives to review per the request. This would be part of all the solutions, either picking from a catalogue item, resource, organization or answer to a multiple answer field.

It is likely that reviewers don't have existing REMS user accounts, and certainly it's not possible to select at review request time which one should do the review, because of the email group box requirement, and reviewing organizations have their own job allocation. Therefore inviting a reviewer to review, and connecting the person to the application and/or organization, must be done like in the invite member function.

Last but not least there is the **option 6**. So what if we indeed want to model the registry keeping organizations as REMS organizations and their catalogue items, and/or registries as catalogue items? We could have review requests targeted to REMS organizations, where an organization would define the review email inboxes and field visibility. A reviewer would see all public form fields, and also private fields of the organization they represent. Common fields can be put as workflow form fields, if we add the capability. This is close to what we do with licenses at the moment, one can add them to resources as well as workflows.

## Decision

Let's try **option 6** because we anticipate that there is a need for multiple forms, as well as a wish to model the organizations better inside REMS. It also has the least amount of maintenance work, and some of it can be delegated to reviewer organizations themselves.

The tasks should be
1. organization creation
   - store id, name, description, owners and review-email
   - minimal CRUD functionality
   - optionally store also members as they log in/are invited
2. reviewer invitation
   - when doing a request a reviewer can be invited by email and not exist as REMS user
   - send email with token
   - connect reviewer to correct application (and/or organization) upon logging in and accepting the token
   - other details like in member invitation
3. review request
   - should have a field to send request by email (invite)
   - should have a field to send request to an organization (like invite but email from organization)
   - if request is sent to an organization, reviewer will represent it and have the visibility to some private fields through it
4. privacy additions
   - hide private questions as well as answers
   - make sure form looks nice in the different cases where some parts are hidden
5. bundling changes
   - remove restriction on having the same form
   - cart functionality
   - change resources action
6. application model changes
   - allow multiple forms in the application model
   - backwards compatible change and/or migrations
   - decide whether to group field answers per form
7. mixed organization catalogue items
   - catalogue item can be mixed organization, i.e. workflow, form and resource can be from different organizations
   - the owner and organization owner can create any combination
   - workflow decides which organization handlers do the processing
   - to match this, also a resource can refer to a license from another organization
   - owners and organization owners can see all forms, resources and workflows but organization owner can only edit the ones that are in their organization

![](https://github.com/CSCfi/rems/blob/review-model/docs/review-and-form-model.svg)
