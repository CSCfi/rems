# 018: EGA Integration

Authors: @Macroz

NB: Support is removed 2024-01-18. If a something like this is required in the future, consider using plugins. See https://github.com/CSCfi/rems/issues/3239

## Situation

REMS instances, such as the THL Biobank, wish to synchronize (push) access rights (entitlements) to The European Genome-phenome Archive (EGA).

We have an existing entitlement post function, but that does not support the kind of features that EGA requires (like an API-key per handler, and GA4GH visa format).

REMS also has a permissions API that supports GA4GH style permission visas. This is for the cases where the entitlements are fetched from REMS.

EGA data download tools check the access rights from EGA servers so we must build something new to push the entitlements there.

The EGA Permissions API is [documented here](https://docs.google.com/document/d/1FTzUYAfV5d2a0zoDkbY9Iy_L5NbSAnHeWnmY2NIrY8M/edit?usp=drivesdk).

## Solution

To enable pushing permissions, we have implemented a new v2 entitlement push. It supports multiple simultaneous integrations as well as types, 
of which `:ega` is the only one implemented so far.

The push is done by the same entitlement background task as before.

For each handler there must be an EGA API-key stored in the user secrets, otherwise the entitlements approved by that handler can't be pushed.

For the approver bot we have implemented a CLI command to set the EGA user account.

The userid of the handler must be such that EGA understands it. This should be an ELIXIR user id that the handler links to their EGA id(entity) using external tools.

## Unfinished details

### DAC id

The data access committee (DAC) identifies who has approved the users. 

- There is currently no way to set which DAC is used for pushing the entitlements. 
  - This could be tied to the workflow, but so far we have no way to set workflow metadata. 
  - This could also be in configurable details, but since the resources are mostly modeled by users in the UI, and DAC members may change,
    it would be nice to be able to edit this also in the UI
