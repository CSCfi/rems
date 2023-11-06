(ns rems.service.cache
  (:require [clojure.core.cache.wrapped :as cache]
            [clojure.set :as set]
            [com.rpl.specter :refer [ALL transform]]
            [medley.core :refer [distinct-by]]
            [rems.application.model :as model]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.common.util :refer [index-by keep-keys]]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.db.blacklist :as blacklist]
            [rems.db.catalogue :as catalogue]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.dependencies :as dependencies]))

(defn get-users []
  (users/get-users))

(defn get-user [userid]
  (users/get-user userid))

(defn user-exists? [userid]
  (users/user-exists? userid))

(defn get-users-with-role [role]
  (set/union
   (users/get-users-with-role role)
   (applications/get-users-with-role role)))

(defn get-catalogue-item-licenses [catalogue-item-id]
  (let [item (catalogue/get-localized-catalogue-item catalogue-item-id {})
        workflow-licenses (-> (workflow/get-workflow (:wfid item))
                              (get-in [:workflow :licenses]))]
    (->> (licenses/get-licenses {:items [catalogue-item-id]})
         (keep-keys {:id :license/id})
         (into workflow-licenses)
         (distinct-by :license/id))))

(def fetcher-injections
  {:get-attachments-for-application attachments/get-attachments-for-application
   :get-form-template form/get-form-template
   :get-catalogue-item catalogue/get-expanded-catalogue-item
   :get-config (fn [] rems.config/env)
   :get-license licenses/get-license
   :get-user users/get-user
   :get-users-with-role get-users-with-role
   :get-workflow workflow/get-workflow
   :blacklisted? blacklist/blacklisted?
   ;;:get-attachment-metadata attachments/get-attachment-metadata
   ;;:get-catalogue-item-licenses get-catalogue-item-licenses
   })


(def cache-all-applications "cached list of all applications that exist" (atom nil))

(def cache-full-internal-application-by-id "cache of fetched full applications" (atom nil))

(defn get-localized-catalogue-item [id]
  (catalogue/get-localized-catalogue-item id))

(defn join-catalogue-item [resource]
  (merge resource
         (get-localized-catalogue-item (:catalogue-item/id resource))))

(defn- join-dependencies [application]
  (->> application
       (transform [:application/workflow :workflow.dynamic/handlers ALL] #(users/join-user %))
       #_(transform [:application/resources ALL] join-catalogue-item)))

(defn cache-dissoc! [cache id]
  (swap! cache dissoc id))

(defn get-full-internal-application
  "Returns the full application state without any user permission
  checks and filtering of sensitive information. Don't expose via APIs."
  [application-id]
  (when-not (contains? @cache-full-internal-application-by-id application-id)
    ;; calculate new value
    (when-let [application (some-> (applications/get-simple-internal-application application-id)
                                   (model/enrich-with-injections fetcher-injections)
                                   join-dependencies
                                   (model/apply-privacy-by-roles #{:reporter}))]

      ;; update cache
      (swap! cache-full-internal-application-by-id assoc application-id application)

      ;; watch any changes
      (dependencies/watch! {:application/id [application-id]
                            :workflow/id [(:workflow/id (:application/workflow application))]
                            :form/id (mapv :form/id (:application/forms application))
                            ;; plain resources can't be edited so skip them
                            :catalogue-item/id (mapv :catalogue-item/id (:application/resources application))
                            :license/id (mapv :license/id (:application/licenses application)) ;; licenses can't actually be edited
                            :userid (keys (:application/user-roles application))}

                           (fn callback-cache-dissoc [params]
                             (cache-dissoc! cache-full-internal-application-by-id application-id)
                             (dependencies/unwatch! (:watch/id params))))))

  (get @cache-full-internal-application-by-id application-id))

(defn get-full-internal-applications
  "Returns the full application state of all applications without
  any user permission checks and filtering of sensitive information.
  Don't expose via APIs."
  []
  (when true #_(nil? @cache-all-applications)
        (reset! cache-all-applications
                (->> (applications/get-simple-applications)
                     (mapv (comp get-full-internal-application :application/id))
                     (index-by [:application/id]))))

  (->> @cache-all-applications
       vals
       (sort-by :application/id)))

(defn get-full-personalized-application-for-user
  "Returns the part of application state which the specified user
  is allowed to see. Suitable for returning from public APIs as-is."
  [userid application-id]
  (when-let [application (applications/get-simple-internal-application application-id)]
    (or (some-> application
                :application/id
                get-full-internal-application
                ;;(model/enrich-with-injections fetcher-injections)
                ;;join-dependencies
                (model/apply-user-permissions userid))
        (throw-forbidden))))

(defn get-full-personalized-applications-by-user
  "Returns all the applications where the user is the applicant or member of.

  Returns the part of application state which the specified user
  is allowed to see. Suitable for returning from public APIs as-is."
  [userid]
  (doall (for [application (applications/get-simple-internal-applications-by-user userid)
               :when application
               :let [application (some-> application
                                         :application/id
                                         get-full-internal-application
                                         ;;join-dependencies
                                         (model/apply-user-permissions userid))]]
           application)))

(defn get-full-personalized-applications-with-user
  "Returns all the applications which the user can see. I.e., they are applying, handling or reporting.

  Returns the part of application state which the specified user
  is allowed to see. Suitable for returning from public APIs as-is."
  [userid]
  (->> (for [application (get-full-internal-applications)
             :when (contains? (:application/user-roles application) userid)
             :let [application (-> application
                                   (model/apply-user-permissions userid))]
             :when application]
         application)
       (sort-by :application/id)
       vec))

(defn get-all-application-roles [userid]
  ;; low level cache is enough
  (applications/get-all-application-roles userid))

(defn get-full-public-application [application-id]
  (some->    application-id
             get-full-internal-application
             ;;applications/get-simple-internal-application
             ;;(model/enrich-with-injections fetcher-injections)
             ;;join-dependencies
             model/hide-non-public-information
             (model/apply-privacy-by-roles #{:reporter})))

(defn reset-cache!
  "Empty all the contents from the cache."
  []
  ;; TODO implement
  (reset! cache-all-applications {}))

(defn reload-cache!
  "Empty all the contents from the cache, and then reload to warm up."
  []
  (reset-cache!)
  ;; TODO implement
  )

(comment
  (reset-cache!))
