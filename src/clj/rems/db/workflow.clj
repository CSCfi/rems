(ns rems.db.workflow
  (:require [rems.db.core :as db]))

(defn get-workflows [filters]
  (->> (db/get-workflows)
       (map db/assoc-active)
       (db/apply-filters filters)))
