(ns rems.service.application
  (:require [rems.csv :as csv]
            [rems.db.applications :as applications]
            [rems.service.cache :as cache]))

(defn get-full-internal-applications
  "Gets all the fully populated applications in the internal raw format.

  The returned applications are not personalized to any user."
  []
  (cache/get-full-internal-applications))

(defn get-full-internal-application
  "Get the fully populated application in the internal raw format.

  The returned application is not personalized to any user."
  [application-id]
  (cache/get-full-internal-application application-id))

(defn get-full-public-application
  "Get the fully populated application in the public raw format.

  The returned application is not personalized to any user."
  [application-id]
  (cache/get-full-public-application application-id))

(defn get-full-personalized-applications-by-user
  "Get the full applications that are made by the user with `userid`.

  The returned applications are personalized to contain what the user can see."
  [userid]
  (cache/get-full-personalized-applications-by-user userid))

(defn get-full-personalized-applications-with-user
  "Get full applications that concern the user with `userid`.

  The returned applications are personalized to contain what the user can see."
  [userid]
  (cache/get-full-personalized-applications-with-user userid))

(defn allocate-application-ids! [time]
  (applications/allocate-application-ids! time))

(defn get-application-id-by-invitation-token [invitation-token]
  (applications/get-application-id-by-invitation-token invitation-token))

(defn export-applications-for-form-as-csv [userid form-id language]
  (let [applications (get-full-personalized-applications-with-user userid)
        filtered-applications (filter #(contains? (set (map :form/id (:application/forms %))) form-id) applications)]
    (csv/applications-to-csv filtered-applications form-id language)))

(defn get-full-personalized-application-for-user [userid application-id]
  (cache/get-full-personalized-application-for-user userid application-id))

(defn get-latest-event []
  (applications/get-latest-event))

(defn get-all-application-roles [userid]
  (cache/get-all-application-roles userid))

(defn get-all-events-since [event-id]
  (applications/get-all-events-since event-id))

(defn delete-application! [application-id]
  (applications/delete-application! application-id))

(comment
  (mount.core/start #'rems.config/env #'rems.db.core/*db* #'rems.locales/translations)
  (get-full-internal-application 1)
  (rems.db.core/get-application-events 16)
  (rems.db.applications/get-application-events 16)
  (rems.application.model/build-application-view (rems.db.applications/get-application-events 16))
  (rems.db.core/get-applications-in-events)
  (first (rems.db.applications/get-simple-applications))
  (first (rems.db.applications/get-applications))
  (get-full-internal-applications)
  (get-full-personalized-applications-with-user "alice")
  (get-full-personalized-applications-with-user "handler")
  (rems.service.todos/get-handled-todos "handler"))

(comment
  (roles/get-roles "alice")
  (organizations/get-all-organization-roles "alice")
  (workflow/get-all-workflow-roles "alice")
  (rems.service.application/get-all-application-roles "alice"))

