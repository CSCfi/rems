(ns rems.service.dependencies
  "Tracking dependencies between entities."
  (:require [clojure.set]
            [rems.common.dependency :as dep]
            [rems.common.util :refer [build-index]]
            [rems.db.catalogue]
            [rems.db.form]
            [rems.db.licenses]
            [rems.db.organizations]
            [rems.db.resource]
            [rems.db.workflow]
            [rems.db.category]))

(defn- list-dependencies []
  (concat

   (for [lic (rems.db.licenses/get-licenses)]
     {:from {:license/id (:id lic)}
      :to (list
           {:organization/id (:organization lic)})})

   (for [form (rems.db.form/get-form-templates)]
     {:from {:form/id (:form/id form)}
      :to (list
           {:organization/id (-> form :organization :organization/id)})})

   (for [res (rems.db.resource/get-resources)]
     {:from {:resource/id (:id res)}
      :to (list
           {:organization/id (-> res :organization :organization/id)}
           (for [lic (:licenses res)]
             {:license/id (:license/id lic)}))})

   (for [cat (rems.db.catalogue/get-catalogue-items)]
     {:from {:catalogue-item/id (:id cat)}
      :to (list
           {:resource/id (:resource-id cat)}
           {:workflow/id (:wfid cat)}
           {:organization/id (-> cat :organization :organization/id)}
           (when-let [id (:formid cat)]
             {:form/id id})
           (for [category (:categories cat)]
             {:category/id (:category/id category)})
           (for [children (:children cat)]
             {:catalogue-item/id (:catalogue-item/id children)}))})

   (for [wf (rems.db.workflow/get-workflows)
         :let [forms (-> wf :workflow :forms)
               licenses (-> wf :workflow :licenses)]]
     {:from {:workflow/id (:id wf)}
      :to (list
           {:organization/id (-> wf :organization :organization/id)}
           (for [form forms]
             {:form/id (:form/id form)})
           (for [lic licenses]
             {:license/id (:license/id lic)}))})

   (for [category (rems.db.category/get-categories)]
     {:from {:category/id (:category/id category)}
      :to (for [child (:category/children category)]
            {:category/id (:category/id child)})})))

(defn db-dependency-graph []
  (reduce #(dep/depend %1 (:from %2) (:to %2))
          (dep/make-graph)
          (list-dependencies)))

(defn- format-dependency [x]
  (cond
    (:license/id x) (select-keys x [:license/id :localizations])
    (:resource/id x) (select-keys x [:resource/id :resid])
    (:workflow/id x) (select-keys x [:workflow/id :title])
    (:catalogue-item/id x) (select-keys x [:catalogue-item/id :localizations])
    (:form/id x) (select-keys x [:form/id :form/internal-name :form/external-title])
    (:organization/id x) (select-keys x [:organization/id :organization/name])
    (:category/id x) (select-keys x [:category/id :category/title])))

(defn enrich-dependency [x]
  (cond
    (:license/id x) (rems.db.licenses/get-license (:license/id x))
    (:resource/id x) (rems.db.resource/get-resource (:resource/id x))
    (:workflow/id x) (rems.db.workflow/get-workflow (:workflow/id x))
    (:catalogue-item/id x) (rems.db.catalogue/get-catalogue-item (:catalogue-item/id x))
    (:form/id x) (rems.db.form/get-form-template (:form/id x))
    (:organization/id x) (rems.db.organizations/getx-organization-by-id (:organization/id x))
    (:category/id x) (rems.db.category/get-category (:category/id x))
    :else (assert false x)))

(defn join-dependency [x]
  (merge x (enrich-dependency x)))

(defn- format-dependencies [deps]
  (let [find-group (partial some {:catalogue-item/id :catalogue-items
                                  :category/id :categories
                                  :form/id :forms
                                  :license/id :licenses
                                  :organization/id :organizations
                                  :resource/id :resources
                                  :workflow/id :workflows})]
    (->> deps
         (build-index {:keys [(comp find-group keys)]
                       :value-fn format-dependency
                       :collect-fn conj}))))

(defn get-all-dependents [item]
  (into #{}
        (remove :archived)
        (dep/get-all-dependents (db-dependency-graph) item)))

(defn- archive-errors
  "Return errors if given item is depended on by non-archived items"
  [item]
  (when-let [dependents (->> (dep/get-all-dependents (db-dependency-graph) item)
                             (eduction (map join-dependency)
                                       (remove :archived))
                             seq)]
    {:success false
     :errors [(merge {:type :t.administration.errors/in-use-by} (format-dependencies dependents))]}))

(defn- unarchive-errors
  "Return errors if given item depends on archived items"
  [item]
  (when-let [dependencies (->> (dep/get-all-dependencies (db-dependency-graph) item)
                               (eduction (map join-dependency)
                                         (filter :archived))
                               seq)]
    {:success false
     :errors [(merge {:type :t.administration.errors/dependencies-archived} (format-dependencies dependencies))]}))

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
  (when-let [dependencies (->> (dep/get-all-dependents (db-dependency-graph) item)
                               (map join-dependency)
                               seq)]
    {:success false
     :errors [(merge {:type :t.administration.errors/in-use-by} (format-dependencies dependencies))]}))
