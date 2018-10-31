Check that all components are responsive and work acceptably in pad or phone

# Technical improvements

## Minor details

./src/cljs/rems/application.cljs:18:;; TODO named secretary routes give us equivalent functions
./src/cljs/rems/application.cljs:19:;; TODO should the secretary route definitions be in

./src/cljs/rems/application.cljs:78: ;; TODO: should this be here?
./src/cljs/rems/application.cljs:217: ;; TODO should this case and perhaps unnecessary mapping from keywords to Bootstrap be removed?
./src/cljs/rems/application.cljs:703: ;; TODO should rename :application
./src/cljs/rems/navbar.cljs:9:;; TODO fetch as a subscription?
./src/cljs/rems/navbar.cljs:11:;; TODO consider moving when-role as it's own component
./src/cljs/rems/navbar.cljs:34: ;;TODO: get navigation options from subscription
./src/clj/rems/db/entitlements.clj:13:;; TODO move Entitlement schema here from rems.api?
./src/clj/rems/db/applications.clj:25:;; TODO cache application state in db instead of always computing it from events
./src/clj/rems/db/applications.clj:225:;; TODO: consider refactoring to finding the review events from the current user and mapping those to applications
./src/clj/rems/db/applications.clj:387: :catalogue-items catalogue-items ;; TODO decide if catalogue-items are part of "form" or "application"
./src/clj/rems/db/applications.clj:693:;; TODO better name
./src/clj/rems/db/applications.clj:694:;; TODO consider refactoring together with judge
./src/clj/rems/api.clj:75: {;; TODO: should this be in rems.middleware?
./src/clj/rems/email.clj:44:;; TODO: send message localized according to recipient's preferences, when those are stored
./src/clj/rems/auth/ldap.clj:36:;; TODO: should stop using "eppn" and instead convert both shibboleth

## Common utils

./src/cljs/rems/cart.cljs:51:;; TODO make util for other pages to use?

# Robustness

./src/clj/rems/events.clj:11:;; TODO handle closing when no draft or anything saved yet

# SQL

./resources/migrations/20170126133032-add-rms-tables.up.sql:54: title varchar(256) NOT NULL, -- TODO: not localized yet, but not used either?
./resources/migrations/20170126133032-add-rms-tables.up.sql:368:-- TODO add foreign key constraints from other tables to user table
./resources/migrations/20170126133032-add-rms-tables.up.sql:375: -- TODO should this have an id for consistency with the other tables?
./resources/sql/queries.sql:208:-- TODO remove resId from this table to make it normalized?
./resources/sql/queries.sql:303:-- TODO: consider renaming this to link-resource-license!
./resources/sql/queries.sql:463:-- TODO: consider refactoring this into get-application-events
