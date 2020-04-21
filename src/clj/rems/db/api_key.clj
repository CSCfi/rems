(ns rems.db.api-key
  (:require [rems.db.core :as db]
            [rems.json :as json]
            [rems.util :refer [update-present]]))

(defn get-api-key [key]
  (-> (db/get-api-key {:apikey key})
      (update-present :users json/parse-string)))

(defn valid? [key user]
  (when-let [key (get-api-key key)]
    (or (nil? (:users key))
        (some? (some #{user} (:users key))))))

(defn add-api-key! [key comment & [users]]
  (db/upsert-api-key! {:apikey key
                       :comment comment
                       :users (when users (json/generate-string users))}))
