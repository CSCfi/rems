(ns rems.db.form-item
  (:require [rems.db.core :as db]))

(defn get-form-items [filters]
  (->> (db/get-all-form-items)
       (map db/assoc-expired)
       (db/apply-filters filters)))
