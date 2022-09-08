# 019: Catalogue Management With Organization Delegation

Authors: @Macroz, @aatkin, @jaakkocsc

## Background

REMS catalogue management is a shared responsibility of all owners and organization owners.

E.g. in [ADR 009](https://github.com/CSCfi/rems/blob/master/docs/architecture/009-review-and-form-model.md) decision it was outlined that:

> 7. mixed organization catalogue items
>     - catalogue item can be mixed organization, i.e. workflow, form and resource can be from different organizations
>     - the owner and organization owner can create any combination
>     - workflow decides which organization handlers do the processing
>     - to match this, also a resource can refer to a license from another organization
>     - owners and organization owners can see all forms, resources and workflows but organization owner can only edit the ones that are in their organization

This approach is in line with the idea that the handler is a superuser, that can do a lot of things themselves. 
The organization owner can do the combinations as they need to, for the catalogue.

However, there is a new user need https://github.com/CSCfi/rems/issues/2967.

## Considerations

- It is clear that an organization owner can edit everything in their own organization.
- It is also clear that there will be only one Catalogue for each REMS instance, so shared management is needed.
- It would be useful to see other organization items, for example to copy or imitate them. If privacy is desired, it is possible to create a new REMS instance for that organization.
- We can have the owner do the catalogue completely, but that would reduce the benefits of the delegated ownership.
- We can have the owner do the shared catalogue items and organizations owners only do catalogue items from within their organization. This however creates a need to copy forms and licenses, where there could be a shared one. If there are many copies, synchonizing changes becomes extra work.
- Consider the name of the attribute `:sharing` or something better. Or `:organization/sharing` etc. Or should it be `:organization/sharing {:sharing/type :public}` which would be the most future-proof. Let's do the latter, which is most future-proof.

## Proposed solution
1. Add to form, workflow, resource and license an attribute `:sharing`, where the value can be `:public` (open for all) or `:private` (only for this organization)
2. Modify create catalogue item component and API to only accept everything from the owner, and mixed organization items where the sharing value is public or it is from your owned organization.
3. Make sure the dropdowns in create catalogue item by default show only the items you can actually use (e.g. public).
4. It should be possible to edit this attribute after an item is created. If an item is public and it is made private, then existing items shall work still and be in the catalogue.

NB: Migration should keep everything as is and the default for new items shall be private, so that sharing is an explicit choice.

## Open questions 
- Decide which tasks to do first, and which ones are left for later.

## Decision
Let's implement the proposed solution, but split into tasks, so we can leave some of the tasks for follow-up, and proceed with a minimal set for the current need.
