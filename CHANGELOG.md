# Changelog

All notable changes to this project will be documented in this file.
Pull requests should add lines under the Unreleased heading if they
have notable changes.

## Unreleased

TODO: fill me

## v2.4 "Tietotie" 2018-10-24

Starting from this milestone releases will also include pre-built war and jar packages.

### Additions
- support for attachment fields on forms
- API endpoint for adding users
### Enhancements
- entitlements API can now be used to fetch applicant's own entitlements
- support for linking to catalogue items by their external resource ids (https://my-rems-instance.org/apply-for?resource=my-resource)
### Changes
- resource api now also returns owner attribute
- configuration and localization has now been externalized from the deployed application (read more at https://rems2docs.rahtiapp.fi/configuration/)
### Breaking Changes
- prefix attribute renamed to organization in API
- backwards compatibility with legacy applications related data has been dropped. Legacy workflows, forms etc. can still be migrated from REMS1 to REMS2
### Fixes
- issue where review buttons weren't rendered correctly
- content created by editor wasn't sometimes shown immediately

## v2.3 "Tekniikantie" 2018-08-29

### Additions
- support for date fields on forms
- filtering functionality for tables
- workflow editor to administration page
- resource editor to administration page
- license editor to administration page
- pdf view for applications
### Enhancements
- support for extra script files with hooks
- enable multiple login saml2 endpoints by providing a link to eds
- Changes
- minor cosmetic changes to status field on application page
- workflows and applications forms now also have an organization prefix field
### Fixes
- current page highlighting

## v2.2 "Vuorimiehentie" 2018-07-13

### Enhancements
- page transitions now have loading animations
- configurable default language
- configurable csv import separation symbol
- header title added to localization
- catalogue item creation from existing form, workflow and resource
### Changes
- catalogue now has a separate "more info" button instead of having items as links
- handled applications are only loaded when "show more" has been clicked
- updates to documentation
### Fixes
- nondeterministic redirect problems for authenticated users

## v2.1 "Otaniementie" 2018-06-25

### Changes
- dependencies updated, the project is now using stable bootstrap 4 and font awesome 5
- changed format of urns and made prefix configurable
- updated demo-data target to be more aligned with test-data
- review request list now ignores users with missing attributes
- namespace cleanup according to coding conventions

### Fixes
- unauthenticated users are redirected to requested url after login
- comments for review request are shown to 3rd party reviewers
- fixed erroneous localization in status change notification
- date to show user time

## v2.0 "Keilaranta" 2018-05-29

First production release with the following major features:

- catalogue with shopping cart
- application sending
- approval process
- 3rd party review
- 3rd party review request
- email notifications
- application bundling
- Saml2 & LDAP login
- API with Swagger
- configurable themes
- configurable localizations
- documentation server
