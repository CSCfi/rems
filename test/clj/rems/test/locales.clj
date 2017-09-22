(ns rems.test.locales
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]))

(defn map-structure
  "Recurse into map m and replace all leaves with true."
  [m]
  (let [transform (fn [v] (if (map? v) (map-structure v) true))]
    (reduce-kv (fn [m k v] (assoc m k (transform v))) {} m)))

(deftest test-all-languages-defined
  (is (= (map-structure (read-string (slurp (io/resource "translations/en-GB.edn"))))
         (map-structure (read-string (slurp (io/resource "translations/fi.edn")))))))
