(ns rems.api.services.dependencies
  "Tracking dependencies between catalogue items, resources, forms, workflows and licenses."
  (:require [medley.core :refer [map-vals]]
            [rems.db.catalogue :as catalogue]
            [rems.db.licenses :as licenses]
            [rems.db.resource :as resource]
            [rems.db.workflow :as workflow]))

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

(defn- list-to-maps [lst]
  {:dependencies
   (map-vals (comp set (partial map :to)) (group-by :from lst))
   :reverse-dependencies
   (map-vals (comp set (partial map :from)) (group-by :to lst))})

(defn dependencies []
  (list-to-maps (list-dependencies)))
