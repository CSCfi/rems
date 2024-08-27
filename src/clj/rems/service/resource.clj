(ns rems.service.resource
  (:require [better-cond.core :as b]
            [clojure.set]
            [com.rpl.specter :refer [ALL transform]]
            [rems.service.dependencies :as dependencies]
            [rems.service.util :as util]
            [rems.config]
            [rems.db.licenses :as licenses]
            [rems.db.organizations :as organizations]
            [rems.db.resource]
            [rems.ext.duo :as duo])
  (:import rems.InvalidRequestException))

(defn- enrich-resource-license [license]
  (-> (licenses/join-license license)
      organizations/join-organization
      (clojure.set/rename-keys {:license/id :id})))

(defn- join-dependencies [resource]
  (when resource
    (->> resource
         organizations/join-organization
         (duo/join-duo-codes [:resource/duo :duo/codes])
         (transform [:licenses ALL] enrich-resource-license))))

(defn get-resource [id]
  (when-let [resource (rems.db.resource/get-resource id)]
    (join-dependencies resource)))

(defn get-resources [filters]
  (->> (rems.db.resource/get-resources filters)
       (mapv join-dependencies)))

(defn ext-id-exists? [ext-id]
  (rems.db.resource/ext-id-exists? ext-id))

(defn- check-duo-codes! [resource]
  (b/when-let [duos (seq (map :id (get-in resource [:resource/duo :duo/codes])))
               unsupported (not-empty (clojure.set/difference (set duos)
                                                              (set (map :id (duo/get-duo-codes)))))]
    (throw (InvalidRequestException.
            (str "Resource contains unsupported DUO codes: " (pr-str unsupported))))))

(defn create-resource! [resource]
  (util/check-allowed-organization! (:organization resource))
  (check-duo-codes! resource)
  (let [id (rems.db.resource/create-resource! resource)]
    ;; reset-cache! not strictly necessary since resources don't depend on anything, but here for consistency
    (dependencies/reset-cache!)
    {:success true
     :id id}))

(defn set-resource-enabled! [{:keys [id enabled]}]
  (util/check-allowed-organization! (:organization (get-resource id)))
  (rems.db.resource/set-enabled! id enabled)
  {:success true})

(defn set-resource-archived! [{:keys [id archived]}]
  (util/check-allowed-organization! (:organization (get-resource id)))
  (or (dependencies/change-archive-status-error archived {:resource/id id})
      (do
        (rems.db.resource/set-archived! id archived)
        {:success true})))
