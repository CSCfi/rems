(ns rems.db.user-mappings
  (:require [clojure.string :as str]
            [rems.db.core :as db]
            [schema.core :as s]))

(s/defschema UserMappings
  {:userid s/Str
   :ext-id-attribute s/Str
   :ext-id-value s/Str})

(def ^:private validate-user-mapping
  (s/validator UserMappings))

(defn get-user-mappings [attribute value]
  (->> (db/get-user-mappings {:ext-id-attribute (name attribute)
                              :ext-id-value value})
       not-empty))

(defn create-user-mapping! [user-mapping]
  (-> user-mapping
      validate-user-mapping
      db/create-user-mapping!))

(defn find-userid
  "Figures out the `userid` of a user reference.

  If a user mapping is found, the corresponding `:userid` is returned.
  Else the string is assumed to be a `userid`."
  [userid-or-ext-id]
  (when-not (str/blank? userid-or-ext-id)
    (let [mappings (db/get-user-mappings {:ext-id-value userid-or-ext-id})  ]
      (assert (< (count mappings) 2) (str "Multiple users found with identity " (pr-str mappings)))
      (or (some :userid mappings)
          userid-or-ext-id))))
