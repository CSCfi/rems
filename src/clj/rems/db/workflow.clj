(ns rems.db.workflow
  (:require [clojure.set :refer [superset?]]
            [rems.db.core :as db]
            [clj-time.core :as time]))

(defn contains-all-kv-pairs? [supermap map]
  (superset? (set supermap) (set map)))

(defn assoc-active [workflow]
  (assoc workflow :active? (and (or (nil? (:start workflow))
                                    (time/after? (time/now) (:start workflow)))
                                (or (nil? (:endt workflow))
                                    (time/before? (time/now) (:endt workflow))))))

(defn get-workflows
  ([] (get-workflows nil))
  ([filters]
   (let [filters (or filters {})]
     (->> (db/get-workflows)
          (map assoc-active)
          (filter #(contains-all-kv-pairs? % filters))))))
