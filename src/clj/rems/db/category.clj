(ns rems.db.category
  (:require [clojure.tools.logging :as log]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [rems.common.util :refer [build-index]]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [medley.core :refer [assoc-some]]))

(s/defschema CategoryData
  {(s/optional-key :description) schema-base/LocalizedString
   (s/optional-key :children) [{:id s/Int}]})

(def ^:private validate-categorydata
  (s/validator CategoryData))

(s/defschema CategoryDb
  (-> {:id s/Int
       :organization schema-base/OrganizationId}
      (merge CategoryData)))

(def ^:private coerce-CategoryDb
  (coerce/coercer! CategoryDb coerce/string-coercion-matcher))

(defn- format-category [category]
  (let [categorydata (json/parse-string (:categorydata category))]
    (-> category
        (update :organization (fn [organization-id] {:organization/id organization-id}))
        (dissoc :categorydata)
        (merge categorydata))))

(def ^:private categories-cache (atom nil))

(defn reload-cache! []
  (log/info :start #'reload-cache!)
  (reset! categories-cache
          (build-index {:keys [:id] :value-fn identity}
                       (map (comp coerce-CategoryDb format-category)
                            (db/get-categories))))
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

(defn- get-categorydata [{:keys [children description]}]
  (-> {}
      (assoc-some :children children)
      (assoc-some :description description)))

(defn- categorydata->json [category]
  (-> (get-categorydata category)
      validate-categorydata
      json/generate-string))

(defn create-category! [category]
  (let [id (:id (db/create-category! {:organization (get-in category [:organization :organization/id])
                                      :categorydata (categorydata->json category)}))]
    (reload-cache!)
    id))

(defn update-category! [id category]
  (let [id (:id (db/update-category! {:id id
                                      :organization (get-in category [:organization :organization/id])
                                      :categorydata (categorydata->json category)}))]
    (reload-cache!)
    id))

(defn delete-category! [id]
  (:id (db/delete-category! {:id id}))
  (reload-cache!))