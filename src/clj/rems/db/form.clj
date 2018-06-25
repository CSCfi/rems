(ns rems.db.form
  (:require [rems.db.core :as db]))

(defn get-forms
  ([filters]
   (let [filters (or filters {})]
     (->> (db/get-forms)
          (map db/assoc-active)
          (filter #(db/contains-all-kv-pairs? % filters))))))
