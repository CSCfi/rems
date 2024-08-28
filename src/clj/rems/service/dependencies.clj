(ns rems.service.dependencies
  "Tracking dependencies between catalogue items, resources, forms, workflows and licenses."
  (:require [rems.common.util :refer [build-index]]
            [rems.db.catalogue]
            [rems.db.form]
            [rems.db.licenses]
            [rems.db.organizations]
            [rems.db.resource]
            [rems.db.workflow]
            [rems.db.category]))

(defn enrich-dependency [dep]
  (cond
    (:license/id dep) (rems.db.licenses/get-license (:license/id dep))
    (:resource/id dep) (rems.db.resource/get-resource (:resource/id dep))
    (:workflow/id dep) (rems.db.workflow/get-workflow (:workflow/id dep))
    (:catalogue-item/id dep) (rems.db.catalogue/get-localized-catalogue-item (:catalogue-item/id dep))
    (:form/id dep) (rems.db.form/get-form-template (:form/id dep))
    (:organization/id dep) (rems.db.organizations/getx-organization-by-id (:organization/id dep))
    (:category/id dep) (rems.db.category/get-category (:category/id dep))
    :else (assert false dep)))

(defn- list-dependencies []
  (concat

   (for [lic (rems.db.licenses/get-licenses nil)
         dep [{:organization/id (:organization lic)}]]
     {:from {:license/id (:id lic)} :to dep})

   (for [form (rems.db.form/get-form-templates nil)
         dep [(:organization form)]]
     {:from {:form/id (:form/id form)} :to dep})

   (for [res (rems.db.resource/get-resources {})
         dep (concat
              (:licenses res)
              [(:organization res)])]
     {:from {:resource/id (:id res)} :to dep})

   (for [cat (rems.db.catalogue/get-localized-catalogue-items {:archived true :expand-catalogue-data? true})
         dep (concat [{:form/id (:formid cat)}
                      {:resource/id (:resource-id cat)}
                      {:workflow/id (:wfid cat)}
                      {:organization/id (:organization cat)}]
                     (for [category (:categories cat)]
                       {:category/id (:category/id category)}))
         :when (some? (val (first dep)))] ; remove nil dependencies, e.g. optional formid
     {:from {:catalogue-item/id (:id cat)} :to dep})

   (for [workflow (rems.db.workflow/get-workflows)
         dep (concat
              (->> (get-in workflow [:workflow :licenses])
                   (mapv #(select-keys % [:license/id])))
              (get-in workflow [:workflow :forms])
              [(:organization workflow)])]
     {:from {:workflow/id (:id workflow)} :to dep})

   (for [cat (rems.db.category/get-categories)
         dep (mapv (fn [category] {:category/id (:category/id category)}) (:category/children cat))]
     {:from {:category/id (:category/id cat)} :to dep})))

(defn- add-status-bits [dep]
  (merge dep
         (select-keys (enrich-dependency dep) [:archived :enabled])))

(defn- list-to-maps [lst]
  {:dependencies (build-index {:keys [:from] :value-fn :to :collect-fn set} lst)
   :reverse-dependencies (build-index {:keys [:to] :value-fn :from :collect-fn set} lst)})

(defn- compute-dependencies []
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

;; For now, all public uses are via the error helpers below
(defn- dependencies []
  (when-not @dependencies-cache
    (reset! dependencies-cache (compute-dependencies)))
  @dependencies-cache)

(defn reset-cache! []
  (reset! dependencies-cache nil))

;; TODO change format of errors so we can get rid of this conversion
(defn- format-deps-for-errors [deps]
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
                 {:forms [(select-keys (enrich-dependency dep) [:form/id :form/internal-name :form/external-title])]}

                 (:organization/id dep)
                 {:organizations [(select-keys (enrich-dependency dep) [:organization/id :organization/name])]}

                 (:category/id dep)
                 {:categories [(select-keys (enrich-dependency dep) [:category/id :category/title])]}))))

(defn- archive-errors
  "Return errors if given item is depended on by non-archived items"
  [item]
  (when-let [users (->> (get-in (dependencies) [:reverse-dependencies item])
                        (mapv add-status-bits)
                        (remove :archived)
                        seq)]
    {:success false
     :errors [(merge {:type :t.administration.errors/in-use-by}
                     (format-deps-for-errors users))]}))

(defn- unarchive-errors
  "Return errors if given item depends on archived items"
  [item]
  (when-let [used (->> (get-in (dependencies) [:dependencies item])
                       (mapv add-status-bits)
                       (filter :archived)
                       seq)]
    {:success false
     :errors [(merge {:type :t.administration.errors/dependencies-archived}
                     (format-deps-for-errors used))]}))

(defn change-archive-status-error
  "Returns an error structure {:success false :errors [...]} if
    - archived is true and item is used by non-archived items
    - archived is false and item uses archived items"
  [archived item]
  (if archived
    (archive-errors item)
    (unarchive-errors item)))

(defn in-use-error
  "Returns an error structure {:success false :errors [...]} if given item is depended on by anything"
  [item]
  (when-let [users (seq (get-in (dependencies) [:reverse-dependencies item]))]
    {:success false
     :errors [(merge {:type :t.administration.errors/in-use-by}
                     (format-deps-for-errors users))]}))
