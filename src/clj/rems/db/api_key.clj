(ns rems.db.api-key
  (:require [rems.db.core :as db]
            [rems.json :as json]))

(defn valid? [key]
  (not (nil? (db/get-api-key {:apikey key}))))

(defn add-api-key! [key permitted-roles comment]
  (db/add-api-key! {:apikey key
                    :comment comment
                    :permittedroles (json/generate-string permitted-roles)}))

(defn permitted-roles [key]
  (set (mapv keyword (json/parse-string (:permittedroles (db/get-api-key {:apikey key}))))))
