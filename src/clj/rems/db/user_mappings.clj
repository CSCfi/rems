(ns rems.db.user-mappings
  (:require [clojure.tools.logging :as log]
            [rems.db.core :as db]
            [rems.json :as json]
            [medley.core :refer [deep-merge]]
            [schema.coerce :as coerce]
            [schema.core :as s]))

(s/defschema UserMappingsDataDb
  {:from s/Str
   :from-value s/Str
   :to-value s/Str})

(def ^:private coerce-UserMappingsDataDb
  (coerce/coercer! UserMappingsDataDb coerce/string-coercion-matcher))

(defn- format-user-mappings [user-mappings]
  (let [usermappingsdata (-> (json/parse-string (:usermappingsdata user-mappings))
                             coerce-UserMappingsDataDb)
        {:keys [from from-value to-value]} usermappingsdata]
    {from {from-value to-value}}))

;; TODO: think about cache namespace refactoring
(def ^:private user-mappings-cache (atom nil))

(defn reset-cache! []
  (reset! user-mappings-cache nil))

(defn reload-cache! []
  (log/info :start #'reload-cache!)
  (let [user-mappings (->> (db/get-user-mappings)
                           (map format-user-mappings)
                           (reduce deep-merge {}))]
    (reset! user-mappings-cache user-mappings))
  (log/info :end #'reload-cache!))

(defn get-user-mapping [attr value]
  (when (nil? @user-mappings-cache)
    (reload-cache!))
  (get-in @user-mappings-cache [attr value]))

(defn create-user-mapping! [user-mappings]
  (db/create-user-mapping! {:usermappingsdata (-> (coerce-UserMappingsDataDb user-mappings)
                                                  json/generate-string)})
  (reload-cache!))
