# Changelog

All notable changes to this project will be documented in this file.
Pull requests should add lines under the Unreleased heading if they
have notable changes.

## Unreleased

Changes since v2.7

### Breaking changes
- Betters support for organizations (#1893)
  - Backend checks that organizations of resource, workflow and form match when creating a catalogue item

### Changes
- Duplicate resource external ids are now allowed (#1988)

### Additions
- Applicant/member notification email address is now shown to handler (#1983)

### Fixes
- Filenames are now retained when downloading attachments (#2019)

## v2.7 "Koivuviidantie" 2020-02-03

### Breaking changes
- Removed support for LDAP authentication
- `/api/workflows/create` API: the `type` parameter's allowed value was changed from `dynamic` to `workflow/dynamic`
- `/api/applications/comment` API renamed to `/api/applications/review`
- `:application.event/commented` event renamed to `:application.event/reviewed`
- `/api/applications/request-comment` API renamed to `/api/applications/request-review` and its `commenters` parameter renamed to `reviewers`
- `:application.event/comment-requested` event renamed to `:application.event/review-requested` and its `:application/commenters` field renamed to `:application/reviewers`
- `/api/applications/commenters` API renamed to `/api/applications/reviewers`
- field/id is now a string. This considers creating forms and the form API, but also form users may have the assumption of integers.
- Better support for organizations (#1893). This is still work in progress. Implemented so far:
  - Tracking of user organizations via the `:organization` attribute from the identity provider
  - List of possible organizations configured with `:organizations` config option
  - When creating a new resource/license/form/workflow/catalogue item there is an organization dropdown instead of a text field
  - Organizations of catalogue item, resource, license, form workflow and catalogue item must match
  - Additional `organization-owner` role that can only edit things belonging to their own organization

### Additions
- Catalogue item form can be changed for one or more items at a time.
  New items will be created that use the new form while the old items
  are disabled and archived. The name of the new item will be exactly
  the same as before. See #837
- Applications can be exported as CSV in admin menu (#1857)
- Added a configuration option for setting a maximum number of days for handling a new application (#1861)
  - Applications that are close to or past the deadline are highlighted on the Actions page
- Added reminder emails. The emails can be sent by calling one of the following
  APIs on a cron schedule or similar. The APIs require an API key. (#1611, #1860)
  - `/api/email/send-handler-reminder` sends email about open applications to all handlers.
  - `/api/email/send-reviewer-reminder` sends email about applications with open review requests to reviewers.
  - `/api/email/send-reminders` sends all of the above emails.
- Allow users to change their email address, in case the identity provider
  doesn't provide an email address or the users want to use a different one (#1884)
- Healthcheck api `/api/health` (#1902)
- Add form field of type 'email', which is validated as an email address (#1894)
- Support www links in form field titles (#1864)
- Have a set of permitted roles for API keys (#1662)
- A `user-owner` role that can only create and edit users
- Fields can be defined public or private. The latter won't be shown to reviewers.
- More columns for blacklist table, blacklist visible on resource administration page (#1724)
- New "header" form field type (#1805)
- Scrollbar and focus now track moved and created form fields in form editor (#1802 #1803)
- Users can be added and removed from the blacklist in the resource admin page (#1706)
- POSTing entitlements to entitlement-target is now retried (#1784)
- [Rejecter bot](docs/bots.md), which rejects applications where a member is blacklisted for a resource (#1771)
- "Assign external id" command for setting the id of an application (#1858)
- Configuration `:disable-commands` for disabling commands (#1891)
- Display on the actions page the handlers who are handling an application (#1795)

### Enhancements
- Application search tips hidden behind question mark icon (#1767)
- Redirect to login page when accessing an attachment link when logged out (#1590)
- Form editor: add new field between fields (#1812)
- Entitlements appear immediately instead of after a delay (#1784)
- Show version information in console instead of the page footer (#1785)
- Searching applications by resource external id now possible (#1919)
- Handler can now close applications in the decider workflow (#1938)
- Create form API requests are validated
- Applicant can now close drafts in the decider workflow (#1938)

### Fixes
- More robust email resending (#1750)
- Changes in workflow, catalogue item and blacklist now take effect without a delay (#1851)

## v2.6 "Kalevalantie" 2019-11-12

### Breaking changes
- `:application/external-id` has been made a non-optional field in the
  API and event schemas. All applications should already have an external ID
  since the previous release, so no database migration should be needed.
- The pdf button and API have been removed. We recommend using "print
  to pdf" in your browser.
- The `start`, `end` and `expired` fields have been removed from licenses,
  workflows, resources, and forms.
- API for creating catalogue item and its localizations has been changed.
  There is now a single API call that is used to create both a catalogue
  item and the localizations, namely, /api/catalogue-items/create.
- APIs for editing workflow, catalogue item, form, resource, or license
  have been changed:
  - The API endpoint for editing content (the name and handlers) of a
    workflow is now /api/workflows/edit.
  - The endpoint for archiving or unarchiving a workflow, a catalogue item,
    a form, a resource, or a license is /archived, prefixed with
    /api/workflows, /api/catalogue-item, /api/forms, /api/resources,
    or /api/licenses, respectively.
  - The endpoint for enabling or disabling a workflow, a catalogue item,
    a form, a resource, or a license is /enabled, prefixed with
    /api/workflows, /api/catalogue-items, /api/forms, /api/resources,
    or /api/licenses, respectively.
- API endpoint for editing forms has been changed from
  /api/forms/[form-id]/edit to /api/forms/edit.
- The page addresses are no more prefixed with `/#/`, so for example the address
  of the catalogue page was changed from `/#/catalogue` to `/catalogue` (#1258)
- More consistent user attributes in APIs (e.g. /api/application/:id,
  /api/users/create) (#1726)

### Additions
- New field types: description, option, multiselect
- Setting maximum length for a form field
- Showing changes between two versions of an application
- Show last modified time for applications
- Many improvements in admininistration pages
  - Archiving forms, workflows, licenses and catalogue items
  - Preview for forms
  - Editing workflows
  - "Copy as new" button for forms
  - Form validation error summary (#1461)
- Upload an attachment file for a license (#808)
- Adding and removing members to/from an application (#609, #870)
- More configuration options for themes (e.g. alert colour)
- Track license acceptance per member (#653)
- Optional external id for applications (format "2019/123") (#862)
- Reporter role
- Accessibility improvements: screen reader support etc. (#1172)
- Store user language preference, use chosen language for emails
- Upgraded swagger-ui from 2 to 3
- Extra pages (#472)
- Full-text search for all application content (#873)
- Creating a new application as a copy from an older application (#832)
- Re-naming a catalogue item (#1507)
- Add enable/disable and archive/unarchive buttons to 'View' pages (#1438)
- On the Actions page, highlight when the application is waiting for some
  actions from the user (#1596)
- Optional "More info" link for catalogue items (#1369)
- Show separately for each license if it has been accepted by the member (#1591)
- Show all errors preventing application submission at the same time (#1594)
- Show applicant's previous applications to handler (#1653)
- Support OpenID Connect, for example Auth0
- Handler can close an application whenever after initial submission (#1669)
- Documentation about [user permissions by application state](docs/application-permissions.md)
- Revoking already approved applications (#1661)
  - The applicant and all members will be added to a blacklist
- Userid field in /api/entitlements response
- Approver bot which approves applications automatically, unless the user+resource is blacklisted (#1660)
- Administration view for blacklist
- Read-only access to administration pages for handlers (#1705)
- New "decider workflow" where the handler cannot approve/reject the application, but only the decider can (#1830)

### Enhancements
- Improved version information in footer
- More systematic use of db transactions
- Improved table widget
- Hide language switcher when only one language configured
- Improved table performance: added a "show all rows" button for long tables
- Modal popups have been replaced with flash messages (#1469)
- Email messages now use the application title and full names of users
- Email message texts improved
- Show three latest events as a default on the application page (#1632)
- A change of language persists now after login thanks to a new language setting cookie.
- A returning user will see the login screen in the correct language if he or she has the cookie.
- Event descriptions on application page now use full name and are more thorough (#1634)

### Fixes
- Entitlement API
- Search on the catalogue and admin pages did not support multiple search terms (#1541)
- Hide flash message when changing language so mixed language content is not shown
- Printing application pages now works (except for drafts) (#1643)
- Applicant and administrator can now view attachment licenses (#1676)

## v2.5 "Maarintie" 2019-07-18

### Breaking changes
- Removed support for old round-based workflows
- Removed support for transferring REMS 1 data
- Replace applications API with new one (for dynamic applications)

### Additions
- Dynamic workflows

This is the last release that still supports round based workflows. Please use this version to convert to dynamic workflows. There are also many changes and fixes since last version, which will be listed in the next release.

WARNING! The migration has problems with databases where licenses have been revoked, if the related entitlements are still active. See #1372.

1. Run lein run migrate in rems/ repository. NOTE! If you can't run lein on target server, use an SSH tunnel. Make sure you have no previous tunnels running!
   ssh -L 5432:remsdbserver:5432 remsappserver
   AND then run on your local machine:
   DATABASE_URL="postgresql://user:pw@localhost/db_name" lein run migrate

2. Create a dynamic workflow

3. Check new, dynamic workflow id from database:
   select * from workflow order by start desc;

4. Run on your own machine lein run convert-to-dynamic <dynamic_workflow_id> NOTE! If you can't run lein on target server, see step 1 for tunneling.

5. Verify from database that all applications have the new, dynamic workflow id in column wfid: select * from catalogue_item_application;

6. Go to administration page in UI and archive all non-dynamic workflows. If you do not have admin privileges, add them by adding owner role for yourself into the database:
   insert into roles (userid, role) = ('[userid]', 'owner')
   where [userid] is the eppn of your account (email address).

7. Verify from ui that different kind of applications still work.

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
