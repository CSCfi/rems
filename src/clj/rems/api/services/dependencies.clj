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
  (concat

   (for [res (resource/get-resources {})
         license (:licenses res)]
     {:from {:resource/id (:id res)} :to {:license/id (:id license)}})

   (for [cat (catalogue/get-localized-catalogue-items {:archived true})
         dep [{:form/id (:formid cat)} {:resource/id (:resource-id cat)} {:workflow/id (:wfid cat)}]]
     {:from {:catalogue-item/id (:id cat)} :to dep})

   (for [workflow (workflow/get-workflows {})
         dep (concat
              (mapv (fn [lic] {:license/id (:id lic)}) (:licenses workflow))
              (:forms (:workflow workflow)))]
     {:from {:workflow/id (:id workflow)} :to dep})))

(defn- add-status-bits [dep]
  (merge dep
         (select-keys (enrich-dependency dep) [:archived :enabled])))

(defn- only-id [item]
  (select-keys item [:resource/id :license/id :catalogue-item/id :form/id :workflow/id]))

(defn- list-to-maps [lst]
  {:dependencies
   (map-vals (comp set (partial map :to)) (group-by :from lst))
   :reverse-dependencies
   (map-vals (comp set (partial map :from)) (group-by :to lst))})

(defn compute-dependencies []
  (-> (list-dependencies)
      list-to-maps))

;; A note about caching: It makes sense to cache the dependency graph
;; since we only need to rebuild it when a new item is created.
;; The :archived and :enabled bits are not stored in the graph because
;; that would mean invalidating the whole graph on every status bit
;; change, _or_ implementing partial rebuilding.
;;
;; TODO: is this caching necessary? Computing the dependencies takes
;; 500ms with performance test data.
(def ^:private dependencies-cache (atom nil))

(defn dependencies []
  (when-not @dependencies-cache
    (reset! dependencies-cache (compute-dependencies)))
  @dependencies-cache)

(defn reset-cache! []
  (reset! dependencies-cache nil))

;; TODO change format of errors so we can get rid of this conversion
(defn- format-deps [deps]
  (apply merge-with concat
         (for [dep deps]
           (cond (:license/id dep)
                 {:licenses [(select-keys (enrich-dependency dep) [:id :localizations])]}

                 (:resource/id dep)
                 {:resources [(select-keys (enrich-dependency dep) [:id :resid])]}

                 (:workflow/id dep)
                 {:workflows [(select-keys (enrich-dependency dep) [:id :title])]}

                 (:catalogue-item/id dep)
                 {:catalogue-items [(select-keys (enrich-dependency dep) [:id :localizations])]}

                 (:form/id dep)
                 {:forms [(select-keys (enrich-dependency dep) [:form/id :form/title])]}))))

(defn archive-errors
  "Return errors if given item is depended on by non-archived items"
  [error-key item]
  (when-let [users (->> (get-in (dependencies) [:reverse-dependencies item])
                        (mapv add-status-bits)
                        (remove :archived)
                        seq)]
    [(merge {:type error-key}
            (format-deps users))]))

(defn in-use-errors
  "Returns errors if given item is depended on at all"
  [error-key item]
  (when-let [users (seq (get-in (dependencies) [:reverse-dependencies item]))]
    [(merge {:type error-key}
            (format-deps users))]))

(defn unarchive-errors
  "Return errors if given item depends on archived items"
  [item]
  (when-let [used (->> (get-in (dependencies) [:dependencies item])
                       (mapv add-status-bits)
                       (filter :archived)
                       seq)]
    (let [{:keys [licenses resources workflows catalogue-items forms]} (format-deps used)]
      (concat
       (when licenses
         [{:type :t.administration.errors/license-archived
           :licenses licenses}])
       (when resources
         [{:type :t.administration.errors/resource-archived
           :resources resources}])
       (when workflows
         [{:type :t.administration.errors/workflow-archived
           :workflows workflows}])
       (when forms
         [{:type :t.administration.errors/form-archived
           :forms forms}])
       ;; case not possible:
       (when catalogue-items
         [{:type :catalogue-items-archived
           :catalogue-items catalogue-items}])))))
