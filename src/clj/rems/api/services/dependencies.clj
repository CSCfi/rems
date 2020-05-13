(ns rems.api.services.dependencies
  "Tracking dependencies between catalogue items, resources, forms, workflows and licenses."
  (:require [medley.core :refer [map-vals]]
            [rems.db.catalogue :as catalogue]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.resource :as resource]
            [rems.db.workflow :as workflow]))

(defn enrich-dependency [dep]
  (cond
    (:license/id dep) (licenses/get-license (:license/id dep))
    (:resource/id dep) (resource/get-resource (:resource/id dep))
    (:workflow/id dep) (workflow/get-workflow (:workflow/id dep))
    (:catalogue-item/id dep) (catalogue/get-localized-catalogue-item (:catalogue-item/id dep))
    (:form/id dep) (form/get-form-template (:form/id dep))
    true (assert false dep)))

(defn- list-dependencies []
  (flatten
   (list
    (for [res (resource/get-resources {})
          :let [this {:resource/id (:id res)}]
          license (:licenses res)]
      {:from this :to {:license/id (:id license)}})
    (for [cat (catalogue/get-localized-catalogue-items {})]
      (let [this {:catalogue-item/id (:id cat)}]
        (list {:from this :to {:form/id (:formid cat)}}
              {:from this :to {:resource/id (:resource-id cat)}}
              {:from this :to {:workflow/id (:wfid cat)}})))
    (for [workflow (workflow/get-workflows {})]
      (let [this {:workflow/id (:id workflow)}]
        (list
         (for [license (:licenses workflow)]
           {:from this :to {:license/id (:id license)}})
         (for [form (:forms (:workflow workflow))]
           {:from this :to {:form/id (:form/id form)}})))))))

(defn- add-status-bits [dep]
  (merge dep
         (select-keys (enrich-dependency dep) [:archived :enabled])))

(defn- add-status-bits-to-list [lst]
  (mapv (partial map-vals add-status-bits) lst))

(defn- only-id [item]
  (select-keys item [:resource/id :license/id :catalogue-item/id :form/id :workflow/id]))

(defn- list-to-maps [lst]
  {:dependencies
   (map-vals (comp set (partial map :to)) (group-by (comp only-id :from) lst))
   :reverse-dependencies
   (map-vals (comp set (partial map :from)) (group-by (comp only-id :to) lst))})

;; TODO memoize
(defn dependencies []
  (-> (list-dependencies)
      add-status-bits-to-list
      list-to-maps))
