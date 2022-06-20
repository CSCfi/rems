# 019: Catalogue Management With Organization Delgation

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

- It is clear that an rganization owner can edit everything in their own organizations.
- It is also clear that there will be only one Catalogue for each REMS instance, so shared management is needed.
