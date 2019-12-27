(ns rems.db.api-key
  (:require [rems.db.core :as db]
            [rems.json :as json]))

(defn valid? [key]
  (not (nil? (db/get-api-key {:apikey key}))))

(defn add-api-key! [key unavailable-roles comment]
  (db/add-api-key! {:apikey key
                    :comment comment
                    :unavailableroles (json/generate-string unavailable-roles)}))

(defn unavailable-roles [key]
  (set (mapv keyword (json/parse-string (:unavailableroles (db/get-api-key {:apikey key}))))))
