(ns rems.db.category
  (:require [clojure.tools.logging :as log]
            [clojure.set]
            [clojure.test :refer [deftest is]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [rems.common.util :refer [build-dags build-index replace-key]]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [medley.core :refer [assoc-some]]))

(s/defschema CategoryData
  {:category/title schema-base/LocalizedString
   (s/optional-key :category/description) schema-base/LocalizedString
   (s/optional-key :category/display-order) s/Int
   (s/optional-key :category/children) [schema-base/CategoryId]})

(def ^:private validate-categorydata
  (s/validator CategoryData))

(s/defschema CategoryDb
  (-> {:category/id s/Int}
      (merge CategoryData)))

(def ^:private coerce-CategoryDb
  (coerce/coercer! CategoryDb coerce/string-coercion-matcher))

(defn- format-category [category]
  (let [categorydata (json/parse-string (:categorydata category))]
    (-> category
        (assoc :category/id (:id category))
        (dissoc :categorydata :id)
        (merge categorydata))))

(def ^:private categories-cache (atom nil))

(defn reset-cache! []
  (reset! categories-cache nil))

(defn reload-cache! []
  (log/info :start #'reload-cache!)
  (let [categories (->> (db/get-categories)
                        (map #(-> (format-category %)
                                  coerce-CategoryDb
                                  (replace-key :id :category/id))))] ; TODO this could be map-keys too
    (reset! categories-cache (build-index {:keys [:category/id]} categories)))
  (log/info :end #'reload-cache!))

(defn get-category
  "Get a single category by id"
  [id]
  (when (nil? @categories-cache)
    (reload-cache!))
  (get @categories-cache id))

(defn get-categories
  "Get all categories"
  []
  (when (nil? @categories-cache)
    (reload-cache!))
  (vals @categories-cache))

(defn- get-categorydata [category]
  (let [display-order (some-> (:category/display-order category)
                              (max 0)
                              (min 1000000))]
    (-> {:category/title (:category/title category)}
        (assoc-some :category/description (:category/description category))
        (assoc-some :category/display-order display-order)
        (assoc-some :category/children (:category/children category)))))

(defn- categorydata->json [category]
  (-> (get-categorydata category)
      validate-categorydata
      json/generate-string))

(defn create-category! [category]
  (let [id (:id (db/create-category! {:categorydata (categorydata->json category)}))]
    (reload-cache!)
    id))

(defn update-category! [id category]
  (let [id (:id (db/update-category! {:id id
                                      :categorydata (categorydata->json category)}))]
    (reload-cache!)
    id))

(defn delete-category! [id]
  (db/delete-category! {:id id})
  (reload-cache!))

(defn enrich-categories [categories]
  (mapv #(get-category (:category/id %)) categories))

(defn get-category-tree []
  (let [categories (get-categories)
        top-level-categories (build-dags {:id-fn :category/id
                                          :child-id-fn :category/id
                                          :children-fn :category/children}
                                         categories)]
    top-level-categories))

(defn get-ancestors-of [id]
  (let [parents (apply merge-with
                       clojure.set/union
                       {}
                       (for [category (get-categories)
                             child (:category/children category)]
                         {(:category/id child) #{(:category/id category)}}))]
    (loop [open (parents id)
           closed #{}]

      (if (empty? open)
        closed

        (let [node-id (first open)]
          (recur (into (rest open) (parents node-id))
                 (conj closed node-id)))))))

(deftest test-get-ancestors-of []
  (with-redefs [get-categories (constantly [{:category/id :a :category/children [{:category/id :b} {:category/id :d}]}
                                            {:category/id :b :category/children [{:category/id :c}]}
                                            {:category/id :c}
                                            {:category/id :d :category/children [{:category/id :e}]}
                                            {:category/id :e :category/children [{:category/id :f}]}
                                            {:category/id :f}])]
    (is (= #{} (get-ancestors-of :not-found)))
    (is (= #{:a} (get-ancestors-of :b)))
    (is (= #{:a :b} (get-ancestors-of :c)))
    (is (= #{:a :d :e} (get-ancestors-of :f))))

  (with-redefs [get-categories (constantly [{:category/id :a}])]
    (is (= #{} (get-ancestors-of :not-found)))
    (is (= #{} (get-ancestors-of :a)))))
