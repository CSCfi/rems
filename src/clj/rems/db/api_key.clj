(ns rems.db.api-key
  (:require [rems.db.core :as db]
            [rems.json :as json]))

(def +all-roles+ #{:applicant :decider :handler :logged-in
                   :owner :past-reviewer :reporter :reviewer
                   :user-owner :organization-owner})

(defn valid? [key]
  (not (nil? (db/get-api-key {:apikey key}))))

(defn add-api-key! [key comment]
  (db/upsert-api-key! {:apikey key
                       :comment comment
                       :permittedroles (json/generate-string +all-roles+)}))
