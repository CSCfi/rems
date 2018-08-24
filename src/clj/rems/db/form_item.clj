(ns rems.db.form-item
  (:require [rems.db.core :as db]))

(defn get-form-items [filters]
  (let [filters (or filters {})]
    (->> (db/get-all-form-items)
         ; TODO: deduplicate with get-forms
         (map db/assoc-active)
         (filter #(db/contains-all-kv-pairs? % filters)))))
