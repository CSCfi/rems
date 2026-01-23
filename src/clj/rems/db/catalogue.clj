(ns rems.db.catalogue
  (:require [clj-time.core :as time]
            [clojure.test :refer [deftest is testing]]
            [clojure.set]
            [medley.core :refer [assoc-some]]
            [rems.cache :as cache]
            [rems.common.util :refer [index-by]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.core :as s]
            [schema.coerce :as coerce]))

(s/defschema CatalogueItemData
  (s/maybe {(s/optional-key :categories) [schema-base/CategoryId]
            (s/optional-key :children) [{:catalogue-item/id s/Int}]}))

(def ^:private validate-catalogueitemdata
  (s/validator CatalogueItemData))

(def ^:private coerce-CatalogueItemData
  (coerce/coercer! CatalogueItemData coerce/string-coercion-matcher))

(defn- format-localization [x]
  (update x :langcode keyword))

(def catalogue-item-localizations-cache
  (cache/basic {:id ::catalogue-item-localizations-cache
                :miss-fn (fn [id]
                           (if-let [localizations (seq (db/get-catalogue-item-localizations {:id id}))]
                             (->> localizations
                                  (map format-localization)
                                  (index-by [:langcode]))
                             cache/absent))
                :reload-fn (fn []
                             (->> (db/get-catalogue-item-localizations)
                                  (map format-localization)
                                  (index-by [:id :langcode])))}))

(defn- parse-catalogue-item-raw [x]
  (let [data (-> (:catalogueitemdata x) json/parse-string coerce-CatalogueItemData)
        cat (-> {:id (:id x)
                 :start (:start x)
                 :end (:end x)
                 :archived (:archived x)
                 :enabled (:enabled x)
                 :localizations {}
                 :resource-id (:resource-id x)
                 :wfid (:workflow-id x)
                 :formid (:form-id x)
                 :organization {:organization/id (:organization-id x)}}
                (assoc-some :categories (not-empty (:categories data))))]
    cat))

(deftest test-parse-catalogue-item-raw
  ;; omit keys without parsing logic associated with them
  (testing "with category"
    (is (= {:categories [{:category/id 2}]}
           (-> {:catalogueitemdata "{\"categories\":[{\"category/id\":2}]}"}
               parse-catalogue-item-raw
               (select-keys [:categories :children])))))

  (testing "with declared key but empty value"
    (is (= {:categories [{:category/id 2}]}
           (-> {:catalogueitemdata "{\"categories\":[{\"category/id\":2}],\"children\":[]}"}
               parse-catalogue-item-raw
               (select-keys [:categories :children])))
        "only those keys that have a value are included")

    (is (-> {:catalogueitemdata "{\"categories\":[],\"children\":[]}"}
            parse-catalogue-item-raw
            (select-keys [:categories :children])
            empty?)))

  (testing "with disallowed key"
    (is (thrown-with-msg? Exception #"Value cannot be coerced to match schema: \{:bad disallowed-key\}"
                          (parse-catalogue-item-raw {:catalogueitemdata "{\"bad\":\"value\"}"})))))

(def catalogue-item-cache
  (cache/basic {:id ::catalogue-item-cache
                :miss-fn (fn [id]
                           (if-let [cat (db/get-catalogue-item {:id id})]
                             (parse-catalogue-item-raw cat)
                             cache/absent))
                :reload-fn (fn []
                             (->> (db/get-all-catalogue-items)
                                  (map parse-catalogue-item-raw)
                                  (index-by [:id])))}))

(defn now-active?
  ([start end]
   (now-active? (time/now) start end))
  ([now start end]
   (and (or (nil? start)
            (not (time/before? now start)))
        (or (nil? end)
            (time/before? now end)))))

(defn assoc-expired
  "Calculates and assocs :expired attribute based on current time and :start and :end attributes.

   Current time can be passed in optionally."
  ([x]
   (assoc-expired (time/now) x))
  ([now x]
   (assoc x
          :expired
          (not (now-active? now (:start x) (:end x))))))

(defn- localize-catalogue-item [item]
  (let [localizations (cache/lookup-or-miss! catalogue-item-localizations-cache (:id item))]
    (-> item
        (assoc-some :localizations localizations))))

(defn get-catalogue-items []
  (->> (vals (cache/entries! catalogue-item-cache))
       (eduction (map assoc-expired)
                 (map localize-catalogue-item))))

(defn get-catalogue-item [id]
  (some-> (cache/lookup-or-miss! catalogue-item-cache id)
          assoc-expired
          localize-catalogue-item))

(defn catalogueitemdata->json [data]
  (-> (select-keys data [:categories])
      (update :categories (fn [categories] (->> categories (mapv #(select-keys % [:category/id])))))
      validate-catalogueitemdata
      json/generate-string))

(defn create-catalogue-item! [{:keys [archived categories enabled form-id localizations organization-id resource-id start workflow-id]
                               :or {archived false enabled true}}]
  (let [catalogueitemdata (catalogueitemdata->json (assoc-some {} :categories categories))
        id (:id (db/create-catalogue-item! (-> {:archived archived
                                                :enabled enabled
                                                :form form-id
                                                :organization organization-id
                                                :resid resource-id
                                                :wfid workflow-id
                                                :catalogueitemdata catalogueitemdata}
                                               (assoc-some :start start))))]
    (doseq [[langcode localization] localizations]
      (db/upsert-catalogue-item-localization! {:id id
                                               :langcode (name langcode)
                                               :title (:title localization)
                                               :infourl (:infourl localization)}))
    (cache/miss! catalogue-item-localizations-cache id)
    (cache/miss! catalogue-item-cache id)
    id))

(defn edit-catalogue-item! [id {:keys [categories localizations organization-id]}]
  (when organization-id
    (db/set-catalogue-item-organization! {:id id :organization organization-id}))
  (doseq [[langcode localization] localizations]
    (db/upsert-catalogue-item-localization! {:id id
                                             :langcode (name langcode)
                                             :title (:title localization)
                                             :infourl (:infourl localization)}))
  (when categories
    (let [catalogueitemdata (catalogueitemdata->json {:categories categories})]
      (db/set-catalogue-item-data! {:id id :catalogueitemdata catalogueitemdata})))

  (cache/miss! catalogue-item-localizations-cache id)
  (cache/miss! catalogue-item-cache id)
  id)

(defn set-attributes!
  "Convenience function to set one or more attributes."
  [id {:keys [archived enabled endt] :as opts}]
  (let [set-archived? (contains? opts :archived)
        set-enabled? (contains? opts :enabled)
        set-endt? (contains? opts :endt)]

    (when set-archived?
      (db/set-catalogue-item-archived! {:id id :archived (true? archived)}))
    (when set-enabled?
      (db/set-catalogue-item-enabled! {:id id :enabled (true? enabled)}))
    (when set-endt?
      (db/set-catalogue-item-endt! {:id id :end endt}))

    ;; Clear endt in case it has been set in the db. Otherwise we might
    ;; end up with an enabled item that's not active and can't be made
    ;; active via the UI.
    (when (not set-endt?)
      (when set-enabled?
        (db/set-catalogue-item-endt! {:id id :end nil}))))

  (cache/miss! catalogue-item-cache id))
