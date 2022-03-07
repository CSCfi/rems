# Checklist for author

Remove items that aren't applicable, check items that are done.

## Reviewability
- [ ] Link to issue
- [ ] Note if PR is on top of other PR
- [ ] Note if related change in rems-deploy repo
- [ ] Consider adding screenshots for ease of review

## Backwards compatibility
- [ ] API is backwards compatible or completely new
- [ ] Events are backwards compatible _or_ have migrations
- [ ] Config is backwards compatible
- [ ] Feature appears correctly in PDF print

## Documentation
- [ ] Update changelog if necessary
- [ ] API is documented and shows up in Swagger UI
- [ ] Components are added to guide page
- [ ] Update docs/ (if applicable)
- [ ] Update manual/ (if applicable)
- [ ] ADR for major architectural decisions or experiments
- [ ] New config options in config-defaults.edn

## Testing
- [ ] Complex logic is unit tested
- [ ] Valuable features are integration / browser / acceptance tested automatically

## Follow-up
- [ ] New tasks are created for pending or remaining tasks
