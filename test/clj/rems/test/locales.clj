(ns rems.test.locales
  (:require [clojure.test :refer :all]
            [rems.locales :refer [tconfig]]))

(defn map-structure
  "Recurse into map m and replace all leaves with true."
  [m]
  (let [transform (fn [v] (if (map? v) (map-structure v) true))]
    (reduce-kv (fn [m k v] (assoc m k (transform v))) {} m)))

(deftest test-all-languages-defined
  (is (= (map-structure (get-in tconfig [:dict :en-GB]))
         (map-structure (get-in tconfig [:dict :fi])))))
