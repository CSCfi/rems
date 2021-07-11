# Changelog

All notable changes to this project will be documented in this file.
Pull requests should add lines under the Unreleased heading if they
have notable changes.

## Unreleased

Changes since v2.19

## v2.19 "Nahkahousuntie" 2021-06-28

### Fixes
- Pdf generation failed for applications that had table fields with no rows. Now fixed.
- Fix for missing notification-email from user settings

### Additions
- Pdf applications now contain license text (for inline licenses), url (for link licenses) or attachment name (for attachment licenses), and license acceptance status for applicant and members. (#2688)

## v2.18 "Särkiniemientie" 2021-05-18

### Changes
- The "Attachments (zip)" button in the UI now only downloads the current application attachments. Event attachments and previous versions of application attachments are left out. The full zip is still available via the API. (#2453)
- Changes to theming: (#2588)
  - Theme variables are now documented in [resources/config-defaults.edn](resources/config-defaults.edn).
  - The `:nav-color` now simply defaults to `:link-color`. Previously, it defaulted to `:color3` if `:link-color` is unset.
  - The theme variables `:danger-color` (didn't really affect anything) and `:phase-background-active` (wasn't used, overlaps with `:phase-bgcolor-active`) have been removed.
  - The default theme has minor visual changes:
    - color2 is lighter
    - table hover highlight is now dark-on-light instead of light-on-dark
    - link color used in nav bar
  - Some theme-related code was rewritten. There should be no changes to appearance, but bugs are possible.
- Setting `:log-authentication-details` now prints out details for failed OIDC HTTP requests.
- More validations in the API:
  - the values of multiselect fields (/api/applications/save-draft)
  - option and column keys (/api/forms/create, /api/forms/edit)

### Fixes
- Errors for invalid inputs (field values that are too long, invalid email addresses, etc.) are now rendered nicely. Previously the applicant just saw a "Save draft: Failed" message. (#2611)
- The application page no longer jumps to the top after adding an attachment. (#2616)
- Deleting drafts with attachments now works.
- A handler is now considered a handler even before first application comes in.

### Additions
- REMS now supports PostgreSQL version 13. (#2642)
- Experimental GA4GH Permissions API now allows users to query their own permissions via `/api/permissions/:user`. (#2631)
- The handler can now change the applicant of a submitted application. (#2581)
  - This feature can be disabled by adding `:application.command/change-applicant` to `:disable-commands`.
- Multiselect fields can now be used to control visibility of other fields. (#1947)

## v2.17 "Isokaari" 2021-04-12

**NB: This release contains migrations!**

### Breaking changes
- A new form field type "table" is now available. A table has a predefined set of columns, and applicants can fill in as many rows as they wish. **You can not roll back to an earlier release once your database contains applications with filled-in table fields. You will need to fix the database manually.** (#2551)

### Changes
- Forms now have both an internal name as well as a localized title instead of the non-localized title. (#2066)
  - The old style form titles are now deprecated in the API and a migration copies the title to the internal name. **Please, check and optionally change the form titles after the migration!**
  - Prefer the new `internal-name` or `external-title` fields instead.
  - The external title is shown to applicants in the application and internal name used throughout the administration.
  - The API supports the old style for now.
- Answers to conditional fields that are not visible are no longer stored by REMS. The API accepts answers for invisible fields but drops them. The UI does not send answers to invisible fields. (#2574)
- The "Assign external id" action now shows the previous assigned external id. (#2530)
- The form editor UI was reworked to look less cluttered. Many inputs are now hidden behind "Show" buttons by default. (#1899)
- It is now possible to create catalogue items without a form via both the API and the UI. (#2603)

### Fixes
- Searching for applications by the original REMS generated id works, even if another id has been assigned. (#2564)
- GA4GH Visa (output by the experimental /api/permissions API) timestamps are now in seconds, instead of milliseconds. (#2554)
- The REMS `reset` command line command now works even when you have duplicate resource ids in the database. (#2557)
  - In practice, this means that REMS will not recreate the unique constraint on resource ids, even when rolling back to old database schema versions.
- The form editor now checks that table, option and multiselect fields are created with at least one column/option. (#2564)
- The multiselect field label wasn't being bolded.
- Info text icon could appear even though the field description was empty.
- Changes to the default translation for the required form field: does not include an asterisk sign anymore.
- Fixed occasional "Invalid path whitelist entry" error when adding/updating api keys.

### Additions
- The example-theme now uses the Lato font.
- The field id can now be changed in the form editor. (#1804)
- "More info" support for EGA style resources (when :enable-ega flag is set) (#2466)
- Add phone number to field types in the form. (#2552)

## v2.16 "Länsiväylä" 2021-02-04

### Changes
- REMS no longer sends the Server: HTTP header to avoid leaking version information. (#2216)
- Text descriptions of some events in the log were phrased better. The created event also shows the original external id. (#2614)
- REMS now consistently uses the application server clock when creating timestamps. Previously, database time was used in some situations, leading to minor inconsistencies. Requires migration, but does not change visible behaviour. (#2540)

### Fixes
- CSS files are now marked as cacheable by browsers. In v2.15 they were mistakenly marked as uncacheable. (#2484)
- OIDC signing keys are now always fetched on login, fixing issues with OIDC key rotation requiring a REMS restart. (#2497)
- /api/resource/<id>, /api/license/<id> and /api/organization/<id> now return HTTP 404 responses if id is not found.

### Additions
- The browser tests will now fail if there are any accessibility violations. (#2463)
- The OIDC configuration is now validated, and REMS refuses to start without a valid OIDC configuration. See the `:oidc-metadata-url` configuration variable and [configuration.md](docs/configuration.md). (#2519)
- The handler can be shown both the assigned external id and the original REMS generated external id. This behavior can be enabled by changing the `:application-id-column` config to `:generated-and-assigned-external-id`. These have also been added as new values to the data model and the original `:application/external-id` kept as it is. (#2614)
- The navbar can be configured to show a logo image. When the `:navbar-logo-name` config is provided, the logo is shown in the navbar (top navigation menu). This logo also can be customized per language like the regular logo. (#2363)


## v2.15 "Tapiolantie" 2021-01-08

### Changes
- The actions area has been adjusted to work better on small screens. (#2501)
- Copying a draft application will now create a new draft but without a link to the previous (draft) application. (#2496)

### Fixes
- Various HTTP caching issues resolved. Users should no longer get an old app.js from their browser cache. (#2484)
- Fixed link in the "You will need to add an email address to your settings" notification. (#2503)
- Previous application history shown to the handler is now correctly limited to the members of the application. (#2470)

### Additions
- Workflow organization can now be edited. (#2333)

## v2.14.1 "Itätuulentie 2" 2020-12-15

This is a bugfix release for v2.14.

Change since v2.14

### Fixes
- Fixed small issues in the form editor regarding validating empty values and field description (#2399)
- Reading of `:oidc-domain` config option was broken in v2.14, now fixed. (#2489)
- Hide decider/reviewer invitation events from applicant. (#2485)

## v2.14 "Itätuulentie" 2020-12-09

Changes since v2.13

### Breaking changes
- Dropped support for shibboleth authentication. (#1235)
- Dropped support for running REMS under tomcat. Dropped support for building the `rems.war` uberwar. (#1235)

### Changes
- The development login page now uses the actual app styles.
- Changed the translations of the request recipients (now accounts for the singular or plural depending on the request type).
- Drafts can now be submitted for disabled catalogue items. A warning is shown for handlers when viewing an application for a disabled catalogue item. (#2436)
- New drafts can no longer be created for disabled catalogue items. (#2436)
- Empty reviews and remarks can't be sent via the UI anymore. Either a comment or an attachment must be provided. (#2433)
- Application members are sorted by name
- New `:oidc-metadata-url` config option replaces `:oidc-domain`. The old `:oidc-domain` option is still supported for now but will emit a warning. See [docs/configuration.md](docs/configuration.md). (#2462)

### Fixes
- New organizations can be immediately used for creating resources etc. Previously a reload of the page was needed. (#2359)
- Catalogue item editor didn't properly show forms, resources or workflows if they were disabled or archived (#2335)
- Add vertical margins around search field for better readability (#2330)
- Workflow editor didn't properly show forms that were disabled or archived (#2335)
- Check file extensions ignoring case (#2392)
- Fixed `java -jar rems.jar help`. See [docs/installing-upgrading.md](docs/installing-upgrading.md)
- Inconsistencies organization owner logic. (#2441)
- Fix accessibility problems with aria-required attribute placement and increase default link contrast (#2431)
- Small navbar is now properly closed after a link is clicked (#1194)
- Fixed an issue where changing field type to label after entering field description crashes form editor (#2399)
- Catalogue item organization can be edited (#2333)
- Catalogue item editor now starts empty when creating a new item after editing. (#2333)
- Hide organization creation button from non-owners who don't have the right to create organizations
- Fixed exporting an application to PDF when there are multiple attachments in one field. (#2469)

### Additions
- All fields can have an info text, shown if the small icon is clicked. (#1863)
- Experimental permissions API that produces GA4GH Visas is now documented in [docs/ga4gh-visas.md](docs/ga4gh-visas.md)
- OIDC scopes are configurable via `:oidc-scopes`. See [docs/configuration.md](docs/configuration.md).
- REMS now reads GA4GH Passports on login and stores the ResearcherStatus of the user. See [docs/ga4gh-visas.md](docs/ga4gh-visas.md). (#2124)
- Automated accessibility test report using [axe](https://www.deque.com/axe/) (#2263)
- Settings page renamed to Profile, now also contains info about user attributes.
- In docker-entrypoint script `CMD` environment variable may be used instead of `COMMANDS`. `CMD` allows REMS commands with arguments to be used. See [docs/installing-upgrading.md](docs/installing-upgrading.md).
- Deciders and reviewers can now be invited via email. (#2040)
  - New `invite-decider` and `invite-reviewer` commands in the API & UI
  - Commands are available to the handler on submitted applications. See [permission table](docs/application-permissions.md).
- The first version of REMS [user manual](manual/)
- Experimental bona fide bot for granting peer-verified ResearcherStatus visas. See [docs/bots.md](docs/bots.md).
- Assign external id button can now be shown for handlers with the `:enable-assign-external-ui` config flag (defaults to `false`). See [resources/config-defaults.edn](resources/config-defaults.edn). (#2476)
- The `:oidc-userid-attribute` configuration option can now contain a list of attributes to try in order. See [docs/configuration.md](docs/configuration.md). (#2366)

## v2.13 "Etelätuulentie" 2020-09-17

*Note!* This is the last release that supports the `:shibboleth` authentication method.

### Breaking changes
- Organizations are maintained in the database and not config. (#2039)
  - See [docs/organizations.md](docs/organizations.md) for more info
- Multiple organization support for users #2035

### Changes
- Returned applications can now be resubmitted even if some catalogue items have been disabled. (#2145)
- Automated browser testing has been improved in implementation and also in the coverage of the administration side
- Form API create & edit requests are validated (#2098). This was meant to be added in 2.7 but the validation wasn't active by mistake.
- Validate application via api on save-draft and validate option list values (#2117)
- Remove assign external id -button from UI
- Clearer help message for close action
- Preserve the white-space in an event comment (#2232)
- Application events are now presented in chronological order instead of grouping requests and responses together.
  In addition there is now a possibility to highlight related events. (#2233)
- Rejecter-bot now rejects existing open applications when a user gets added to a blacklist either manually or via the revoke command. (#2015)
- Reporter can't see draft applications (#2268)
- Better error message for missing organization in admin UI (#2039)
- Improvements to swedish translations

### Fixes
- Various fixes in workflow editor UI
- Form field placeholders now fulfil accessibility contrast ratio requirements (#2229)
- UI for the close action erroneously claimed the comment is not shown to the applicant. (#2212)
- Description of the Decider workflow erroneously claimed that application can not be closed.
- Redirecting the user back to the page they landed on after login now works even with OIDC authentication. (#2247)
- Fixed enabling a catalogue item after changing its form. (#2283)
- Added missing decision text to pdf event list.
- More compatible CSV reports. Line returns are removed from field values and CSV lines are separated with CRLF. (#2311)
- Fixed editing a catalogue item. (#2321)

### Additions
- The form administration pages now flag forms that have missing localizations. REMS also logs a warning on startup for these forms. (#2098)
- There is now an API for querying and creating organizations. (#2039)
- Possibility to access `/catalogue` without logging in. Configurable by `:catalogue-is-public`. (#2120)
- Workflows can now have forms. Workflow forms apply to all catalogue items that use the workflow. (#2052)
- Applicants now get emails when a public remark is added to an application. (#2190)
- All emails sent by REMS now have the Auto-Submitted header set. (#2175)
- OIDC access tokens are now revoked on logout if the OIDC server provides a `revocation_endpoint`. (#2176)
- Application attachment fields now accept multiple attachments. (#2122)
- It's now possible to add a text to the login page after the login button using extra translations (:t.login/intro2) (#2214)
- Indicate which items are in shopping cart by changing add button to remove (#2228)
- Applicants now receive an email when submitting an application. (#2234)
- Organisations can be created and edited in the UI. (#2039, #2332)
- The /apply-for redirect supports multiple resources. See [docs/linking.md](docs/linking.md). (#2245)
- REMS can now store and show additional user attributes from OIDC. These attributes are only shown to handlers, owners etc. and not applicants. See [docs/configuration.md](docs/configuration.md). (#2130)
- The OIDC attribute to use as the rems userid is now configurable via the `:oidc-userid-attribute`. See [docs/configuration.md](docs/configuration.md). (#2281)
- The `:oidc-additional-authorization-parameters` config option. See [config-defaults.edn](resources/config-defaults.edn)
- Applicants can now permanently delete drafts. (#2219)
- When approving an application, the handler can optionally pick an end date for the entitlement. There is also a `:entitlement-default-length-days` configuration variable that is used to compute a default value for the end date. (#2123)
- Better documentation related to organizations. (#2039)
- The reporter role now has read-only access to administration APIs and pages. (#2313)

## v2.12 "Merituulentie" 2020-05-04

### Breaking changes
- API key authorization has been reworked. API keys no longer have a
  set of roles associated with them, instead each API key can have an
  optional user and API path whitelists.
  See [docs/using-the-api.md](docs/using-the-api.md). (#2127)

### Changes
- Login component and its texts have changed to a more simplified look. Please, remember to update your extra translations to match.
- Development login configuration is changed from `:fake-shibboleth` to `:fake` and styled like OIDC login
- Improvements to PDFs (#2114)
  - show attachment file names
  - list instead of table for events
  - hide draft-saved events
  - vertical space around form fields
  - PDF button moved to Actions pane

### Fixes
- Long attachment filenames are now truncated in the UI (#2118)
- `/api/applications/export` now doesn't blow up when an application has multiple forms. Instead only answers for the requested form are returned. (#2153)
- Sort applications based on the application external id by sequence (#2183)

### Additions
- Downloading all attachments as a zip file (API `/api/applications/:id/attachments`, button in UI) (#2075)
- Event notifications over HTTP. See [docs/event-notification.md](docs/event-notification.md) for details. (#2095)
- Audit log for all API calls in the database. Can be queried via `/api/audit-log` by the `reporter` role. (#2057)
- `/api/applications/export` is now allowed for the `reporter` role (previously only `owner`)

## v2.11 "Kotitontuntie" 2020-04-07

### Additions
- REMS sessions now stay alive while the user is active in the browser (#2107).
- The `/api/users/active` API lists which users have active sessions at the moment.

## v2.10 "Riihitontuntie" 2020-04-06

### Additions
- Swedish localizations. They can be enabled by adding `:sv` to the `:languages` config option. (#1892)

### Fixes
- REMS now exits with status 0 on SIGINT and SIGTERM
- REMS now sets PostgreSQL `lock_timeout` (configurable, defaults to 10s) and `idle_in_transaction_session_timeout` (configurable, defaults to 20s) to avoid deadlocks (#2101)

## v2.9 "Olarinluoma" 2020-03-26

### Breaking changes
- Multiple form support #2043
  - Catalogue items that share a workflow but have different forms can now be bundled into one application.
  - Migrations will update the data. API changes are listed here.
  - Applications used to contain the key `application/form` but now will contain `application/forms` where there is a sequence of forms.
  - Commands with `field-values` will have a `form` in addition to `field` and `value`.
  - Events with `form/id` will have a `application/forms` where each has a `form/id`.

### Changes
- Removed requirement for organizations to match when creating catalogue item or resource (#1893). This reverts the only breaking change in 2.8.
- Allow organization owners to edit resources, forms, licenses and workflows in their own organization (#1893)
- Show resources, forms, licenses and workflows from all organizations to organization owners (#1893)
- API: comments are now optional for commands

### Additions
- Generating bare-bones PDFs from applications. This is a non-experimental feature. Fancier PDF generation is still experimental and can be enabled with a flag. (#2053)
- It is possible to add attachments to most actions that have a comment field (#1928)
- Added `list-users` and `grant-role` commands for `rems.jar`. For details see <docs/installing_upgrading.md> (#2073)
- A warning is now logged when the config file contains unrecognized keys.

### Fixes
- Excel and OpenOffice files are now really allowed as attachments. Also, .csv and .tsv are allowed. Allowed file extensions are documented in the UI. (#2023)
- Attachments now get copied when copying an application (#2056)

## v2.8 "Mankkaanlaaksontie" 2020-03-03

### Breaking changes
- Betters support for organizations (#1893)
  - Backend checks that organizations of license, resource, workflow and form match when creating a catalogue item or resource

### Changes
- Duplicate resource external ids are now allowed (#1988)

### Additions
- Applicant/member notification email address is now shown to handler (#1983)
- Allow Excel and OpenOffice files as attachments (#2023)

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
