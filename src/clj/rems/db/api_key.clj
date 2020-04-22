(ns rems.db.api-key
  (:require [rems.db.core :as db]
            [rems.json :as json]
            [rems.util :refer [update-present]]))

(defn- format-api-key [key]
  (-> key
      (update-present :users json/parse-string)
      (update-present :paths json/parse-string)))

(defn get-api-key [key]
  (format-api-key (db/get-api-key {:apikey key})))

(defn get-api-keys []
  (mapv format-api-key (db/get-api-keys {})))

(defn- path-matches [path pattern]
  (re-matches (re-pattern pattern) path))

(defn valid? [key user path]
  (when-let [key (get-api-key key)]
    (and (or (nil? (:users key))
             (some? (some #{user} (:users key))))
         (or (nil? (:paths key))
             (some? (some (partial path-matches path) (:paths key)))))))

(defn add-api-key! [key & [{:keys [comment users paths]}]]
  (db/upsert-api-key! {:apikey key
                       :comment comment
                       :users (when users (json/generate-string users))
                       :paths (when paths (json/generate-string paths))}))

(defn update-api-key! [key & [opts]]
  (add-api-key! key (merge (get-api-key key)
                           opts)))
