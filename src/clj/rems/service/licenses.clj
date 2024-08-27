(ns rems.service.licenses
  "Serving licenses for API."
  (:require [rems.service.dependencies :as dependencies]
            [rems.service.util :as util]
            [rems.db.licenses :as licenses]
            [rems.db.organizations :as organizations]))

(defn create-license! [{:keys [licensetype organization localizations]}]
  (util/check-allowed-organization! organization)
  (let [id (rems.db.licenses/create-license! {:license-type licensetype
                                              :organization-id (:organization/id organization)
                                              :localizations localizations})]
    ;; reset-cache! not strictly necessary since licenses don't depend on anything, but here for consistency
    (dependencies/reset-cache!)
    {:success (some? id)
     :id id}))

(defn get-license
  "Get a single license by id"
  [id]
  (when-let [license (licenses/get-license id)]
    (-> license
        organizations/join-organization)))

(defn set-license-enabled! [{:keys [id enabled]}]
  (util/check-allowed-organization! (:organization (get-license id)))
  (rems.db.licenses/set-enabled! id enabled)
  {:success true})

(defn set-license-archived! [{:keys [id archived]}]
  (util/check-allowed-organization! (:organization (get-license id)))
  (or (dependencies/change-archive-status-error archived  {:license/id id})
      (do
        (rems.db.licenses/set-archived! id archived)
        {:success true})))

(defn get-all-licenses
  [filters]
  (->> (licenses/get-licenses filters)
       (mapv organizations/join-organization)))
