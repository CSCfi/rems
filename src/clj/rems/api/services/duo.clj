(ns rems.api.services.duo
  (:require [rems.db.duo :as db]))

(def mondo-codes
  (->> (range 100)
       (map (fn [n]
              (let [mondo-code (condp > n
                                 10 (str "MONDO:000000" n)
                                 100 (str "MONDO:00000" n)
                                 1000 (str "MONDO:0000" n)
                                 10000 (str "MONDO:000" n)
                                 100000 (str "MONDO:00" n)
                                 (str "MONDO:0" n))]
                {:id mondo-code
                 :label "mondo code description here"})))))

(defn get-all-duo-codes
  "Get all DUO codes."
  []
  (sort-by :id (db/get-duo-codes)))