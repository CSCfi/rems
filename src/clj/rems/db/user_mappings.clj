(ns rems.db.user-mappings
  (:require [rems.db.core :as db]
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

