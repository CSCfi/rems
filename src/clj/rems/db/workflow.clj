(ns rems.db.workflow
  (:require [rems.db.core :as db]))

(defn get-workflows
  ([filters]
   (let [filters (or filters {})]
     (->> (db/get-workflows)
          (map db/assoc-active)
          (filter #(db/contains-all-kv-pairs? % filters))))))
