(ns rems.db.resource
  (:require [rems.db.core :as db]))

(defn get-resources [filters]
  (->> (db/get-resources)
       (map db/assoc-active)
       (db/apply-filters filters)))
