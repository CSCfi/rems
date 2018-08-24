(ns rems.db.form
  (:require [rems.db.core :as db]))

(defn get-forms [filters]
  (->> (db/get-forms)
       (map db/assoc-active)
       (db/apply-filters filters)))
