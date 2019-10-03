---
---

# Definition of Done / Review checklist

## Reviewability
- [ ] link to issue
- [ ] note if PR is on top of other PR
- [ ] note if related change in rems-deploy repo
- [ ] consider adding screenshots for ease of review

## API
- [ ] API is documented and shows up in Swagger UI
- [ ] API is backwards compatible or completely new
- [ ] Events are backwards compatible

## Documentation
- [ ] update changelog if necessary
- [ ] add or update docstrings for namespaces and functions
- [ ] components are added to guide page
- [ ] documentation _at least_ for config options (i.e. docs folder)
- [ ] ADR for major architectural decisions or experiments

## Different installations
- [ ] new configuration options added to rems-deploy repository
- [ ] instance specific translations (i.e. LBR kielivara)

## Testing
- [ ] complex logic is unit tested
- [ ] valuable features are integration / browser / acceptance tested automatically

## Accessibility
- [ ] all icons have the aria-label attribute
- [ ] all fields have a label
- [ ] errors are linked to fields with aria-describedby
- [ ] contrast is checked
- [ ] conscious decision about where to move focus after an action

## Follow-up
- [ ] new tasks are created for pending or remaining tasks
- [ ] no critical TODOs left to implement
