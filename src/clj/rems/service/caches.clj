(ns rems.service.caches
  (:require [rems.cache :as cache]
            [rems.db.form]
            [rems.db.licenses]
            [rems.db.organizations]
            [rems.db.resource]
            [rems.db.roles]
            [rems.db.users]
            [rems.db.user-mappings]
            [rems.db.user-settings]
            [rems.db.workflow]))

(def db-caches
  "Caches that use existing database."
  #{#'rems.db.form/form-template-cache
    #'rems.db.licenses/license-cache
    #'rems.db.licenses/license-localizations-cache
    #'rems.db.organizations/organization-cache
    #'rems.db.resource/resource-cache
    #'rems.db.roles/role-cache
    #'rems.db.user-mappings/user-mappings-cache
    #'rems.db.user-settings/user-settings-cache
    #'rems.db.users/user-cache
    #'rems.db.workflow/workflow-cache})

(defn start-caches! [& caches]
  (->> caches
       (mapcat identity)
       (run! #(cache/ensure-initialized! (cond-> % (var? %) var-get)))))

(defn reset-caches! [& caches]
  (->> caches
       (mapcat identity)
       (run! #(cache/reset! (cond-> % (var? %) var-get)))))

(defn start-all-caches! [] (start-caches! db-caches))
(defn reset-all-caches! [] (reset-caches! db-caches))
