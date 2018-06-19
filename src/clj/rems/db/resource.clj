(ns rems.db.resource
  (:require [rems.db.core :as db]))

(defn get-resources
  ([] (get-resources nil))
  ([filters]
   (let [filters (or filters {})]
     (->> (db/get-resources)
          (map db/assoc-active)
          (filter #(db/contains-all-kv-pairs? % filters))))))
