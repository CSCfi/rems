(ns rems.service.form
  (:require [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [rems.service.dependencies :as dependencies]
            [rems.service.util :refer [check-allowed-organization!]]
            [rems.common.form :as common-form]
            [rems.config :refer [env]]
            [rems.db.form]
            [rems.db.organizations]))

(defn form-editable [form-id]
  (or (dependencies/in-use-error {:form/id form-id})
      {:success true}))

(defn validation-error [form]
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
    (check-allowed-organization! organization)
    (or (validation-error form)
        (let [form-id (rems.db.form/save-form-template! (migrate-title form (:languages env)))]
          {:success (not (nil? form-id))
           :id form-id}))))

(defn- join-dependencies [form]
  (when form
    (->> form
         rems.db.organizations/join-organization)))

(defn get-form-template [id]
  (-> (rems.db.form/get-form-template id)
      join-dependencies))

(defn get-form-templates [filters]
  (->> (rems.db.form/get-form-templates filters)
       (mapv join-dependencies)))

(defn edit-form! [form]
  ;; need to check both previous and new organization
  (check-allowed-organization! (:organization (get-form-template (:form/id form))))
  (check-allowed-organization! (:organization form))
  (or (dependencies/in-use-error {:form/id (:form/id form)})
      (validation-error form)
      (do (rems.db.form/edit-form-template! form)
          {:success true})))

(defn set-form-enabled! [{:keys [id enabled]}]
  (check-allowed-organization! (:organization (get-form-template id)))
  (rems.db.form/set-enabled! id enabled)
  {:success true})

(defn set-form-archived! [{:keys [id archived]}]
  (check-allowed-organization! (:organization (get-form-template id)))
  (or (dependencies/change-archive-status-error archived {:form/id id})
      (do
        (rems.db.form/set-archived! id archived)
        {:success true})))
