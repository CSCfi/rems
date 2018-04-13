REST API
- API-Key hallinta?

Guide komponentit

Move subscriptions from subscriptions.cljs to appropriate files

Redirect (away from home page when logged in)

Approve/Review modal dialogs

Bootstrap stable

CSRF token?

Check that all components are responsive and work acceptably in pad or phone

re-frame/ or rf/ convention?
string/ str/ or s/ convention?

# Missing features

./src/cljs/rems/spa.cljs:29:     "TODO about page in markdown"]]])

## Hide information
./src/cljs/rems/application.cljs:407:    ;; TODO hide from reviewer
./src/cljs/rems/application.cljs:480:     ;; TODO may-see-event? needs to be implemented in backend
./src/cljs/rems/application.cljs:482:     ;; TODO hide from applicant:
./src/clj/rems/form.clj:16:;; TODO not yet implemented for SPA
./src/clj/rems/db/applications.clj:521:;; TODO should only be able to see the phase if applicant, approver, reviewer etc.

## Notify actors
./src/clj/rems/db/applications.clj:219:;; TODO notify actors also during other events such as reject, return etc.

## Review timeout
./src/clj/rems/db/applications.clj:254:    ;; TODO implement review timeout later



# Technical improvements

## Minor details
./src/cljs/rems/application.cljs:14:;; TODO named secretary routes give us equivalent functions
./src/cljs/rems/application.cljs:15:;; TODO should the secretary route definitions be in 

./src/cljs/rems/application.cljs:519:   ;; TODO: fix applicant-info example when we have roles
./src/cljs/rems/phase.cljs:6:;; TODO compute these on the server side?

./src/cljs/rems/application.cljs:76:          ;; TODO: should this be here?
./src/cljs/rems/application.cljs:192:     ;; TODO should this case and perhaps unnecessary mapping from keywords to Bootstrap be removed?
./src/cljs/rems/application.cljs:467:  ;; TODO should rename :application
./src/cljs/rems/navbar.cljs:9:;; TODO fetch as a subscription?
./src/cljs/rems/navbar.cljs:12:;; TODO consider moving when-role as it's own component
./src/cljs/rems/navbar.cljs:35:  ;;TODO: get navigation options from subscription
./src/clj/rems/db/entitlements.clj:13:;; TODO move Entitlement schema here from rems.api?
./src/clj/rems/db/applications.clj:24:;; TODO cache application state in db instead of always computing it from events
./src/clj/rems/db/applications.clj:207:;; TODO: consider refactoring to finding the review events from the current user and mapping those to applications
./src/clj/rems/db/applications.clj:380:                          :catalogue-items catalogue-items ;; TODO decide if catalogue-items are part of "form" or "application"
./src/clj/rems/db/applications.clj:684:;; TODO better name
./src/clj/rems/db/applications.clj:685:;; TODO consider refactoring together with judge
./src/clj/rems/api.clj:85:   {;; TODO: should this be in rems.middleware?
./src/clj/rems/email.clj:44:;; TODO: send message localized according to recipient's preferences, when those are stored
./src/clj/rems/auth/ldap.clj:36:;; TODO: should stop using "eppn" and instead convert both shibboleth

## Common utils
./src/cljs/rems/cart.cljs:53:;; TODO make util for other pages to use?

## Cleanup: validation already implemented in SPA

./src/clj/rems/form.clj:28:;; TODO not yet implemented for SPA



# Usability improvements

## Spinner while action is ongoing

./src/cljs/rems/application.cljs:304:    ;; TODO nicer styling
./src/cljs/rems/application.cljs:305:    ;; TODO make the spinner spin
./src/cljs/rems/application.cljs:496:    ;; TODO replace with spinner or localize?

## Disable inputs while action is ongoing

./src/cljs/rems/application.cljs:150:     ;; TODO disable form while saving?



# Robustness

./src/cljs/rems/actions.cljs:129:;; TODO ensure ::actions is loaded when navigating to page
./src/clj/rems/events.clj:155:;; TODO handle closing when no draft or anything saved yet

## What if a request fails?

./src/cljs/rems/application.cljs:47:  ;; TODO: handle errors (e.g. unauthorized)
./src/cljs/rems/application.cljs:54:  ;; TODO: handle errors (e.g. unauthorized)
./src/cljs/rems/application.cljs:162:        ;; TODO error handling

## CSRF
./src/cljs/rems/cart.cljs:12:;; TODO anti-forgery when submitting


# SQL

./resources/migrations/20170126133032-add-rms-tables.up.sql:54:  title varchar(256) NOT NULL, -- TODO: not localized yet, but not used either?
./resources/migrations/20170126133032-add-rms-tables.up.sql:368:-- TODO add foreign key constraints from other tables to user table
./resources/migrations/20170126133032-add-rms-tables.up.sql:375:  -- TODO should this have an id for consistency with the other tables?
./resources/sql/queries.sql:159:-- TODO remove resId from this table to make it normalized?
./resources/sql/queries.sql:204:-- TODO: make this atomic
./resources/sql/queries.sql:246:-- TODO: consider renaming this to link-resource-license!
./resources/sql/queries.sql:367:-- TODO: consider refactoring this into get-application-events
./resources/sql/transfer-data.sql:230:-- TODO: better handling of duplicates
./resources/sql/transfer-data.sql:284:-- TODO: handle separation of migrating normal and third party reviews
./env/dev/clj/rems/env.clj:8:;; TODO these could be moved to config.edn
