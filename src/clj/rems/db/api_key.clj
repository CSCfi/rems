(ns rems.db.api-key
  (:require [rems.db.core :as db]
            [rems.json :as json]
            [rems.util :refer [update-present]]))

(defn get-api-key [key]
  (-> (db/get-api-key {:apikey key})
      (update-present :users json/parse-string)
      (update-present :paths json/parse-string)))

;; TODO support for richer patterns
(defn- path-matches? [pattern path]
  (= pattern path))

(defn valid? [key user path]
  (when-let [key (get-api-key key)]
    (and (or (nil? (:users key))
             (some? (some #{user} (:users key))))
         (or (nil? (:paths key))
             (some? (some (partial path-matches? path) (:paths key)))))))

(defn add-api-key! [key & [{:keys [comment users paths]}]]
  (db/upsert-api-key! {:apikey key
                       :comment comment
                       :users (when users (json/generate-string users))
                       :paths (when paths (json/generate-string paths))}))
