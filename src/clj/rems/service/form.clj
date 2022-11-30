(ns rems.service.form
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [rems.service.dependencies :as dependencies]
            [rems.service.util :as util]
            [rems.common.form :as common-form]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.organizations :as organizations]))

(defn form-editable [form-id]
  (or (dependencies/in-use-error {:form/id form-id})
      {:success true}))

;; TODO should this be in rems.db.form?
(defn- validation-error [form]
  (when-let [error-map (common-form/validate-form-template form (:languages env))]
    {:success false
     :errors [error-map]}))

;; TODO remove once deprecation gone
(defn- migrate-title [form languages]
  (if (:form/title form)
    (do (log/warn "Legacy command with :form/title " form)
        (-> form
            (assoc :form/internal-name (:form/title form)
                   :form/external-title (into {} (for [lang languages]
                                                   [lang (:form/title form)])))
            (dissoc :form/title)))
    form))

(deftest test-migrate-title
  (is (= {:form/internal-name "title"
          :form/external-title {:en "title"
                                :fi "title"}}
         (migrate-title {:form/title "title"} [:en :fi]))))

(defn create-form! [form]
  (let [organization (:organization form)]
    (util/check-allowed-organization! organization)
    (or (validation-error form)
        (let [form-id (form/save-form-template! (migrate-title form (:languages env)))]
          ;; reset-cache! not strictly necessary since forms don't depend on anything, but here for consistency
          (dependencies/reset-cache!)
          {:success (not (nil? form-id))
           :id form-id}))))

(defn- join-dependencies [form]
  (when form
    (->> form
         organizations/join-organization)))

(defn get-form-template [id]
  (-> (form/get-form-template id)
      join-dependencies))

(defn get-form-templates [filters]
  (->> (form/get-form-templates filters)
       (mapv join-dependencies)))

(defn edit-form! [form]
  ;; need to check both previous and new organization
  (util/check-allowed-organization! (:organization (get-form-template (:form/id form))))
  (util/check-allowed-organization! (:organization form))
  (or (dependencies/in-use-error {:form/id (:form/id form)})
      (validation-error form)
      (do (form/edit-form-template! form)
          {:success true})))

(defn set-form-enabled! [{:keys [id enabled]}]
  (util/check-allowed-organization! (:organization (get-form-template id)))
  (db/set-form-template-enabled! {:id id :enabled enabled})
  {:success true})

(defn set-form-archived! [{:keys [id archived]}]
  (util/check-allowed-organization! (:organization (get-form-template id)))
  (or (dependencies/change-archive-status-error archived {:form/id id})
      (do
        (db/set-form-template-archived! {:id id
                                         :archived archived})
        {:success true})))
