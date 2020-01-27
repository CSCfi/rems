(ns rems.common.form
  (:require  [clojure.test :refer [deftest is]]
             [medley.core :refer [find-first]]))

(defn- generate-field-ids
  "Generate a set of unique field ids taking into account what have been given already.

  Returns in the format [{:field/id \"fld1\"} ...], same as the fields."
  [fields]
  (let [generated-ids (map #(str "fld" %) (iterate inc 1))
        default-ids (for [id (->> generated-ids
                                  (remove (set (map :field/id fields))))]
                      {:field/id id})]
    default-ids))

(def generate-field-id
  "Generate a single unique field id taking into account what have been given already.

  Returns in the format [{:field/id \"fld1\"} ...], same as the fields."
  (comp first generate-field-ids))

(defn assign-field-ids
  "Go through the given fields and assign each a unique `:field/id` if it's missing."
  [fields]
  (mapv merge (generate-field-ids fields) fields))

(deftest test-assign-field-ids
  (is (= [] (assign-field-ids [])))
  (is (= [{:field/id "fld1"} {:field/id "fld2"}] (assign-field-ids [{} {}])))
  (is (= [{:field/id "abc"}] (assign-field-ids [{:field/id "abc"}])))
  (is (= [{:field/id "abc"} {:field/id "fld2"}] (assign-field-ids [{:field/id "abc"} {}])))
  (is (= [{:field/id "fld2"} {:field/id "fld3"}] (assign-field-ids [{:field/id "fld2"} {}])))
  (is (= [{:field/id "fld2"} {:field/id "fld1"}] (assign-field-ids [{} {:field/id "fld1"}])))
  (is (= [{:field/id "fld2"} {:field/id "fld4"} {:field/id "fld3"}] (assign-field-ids [{:field/id "fld2"} {} {:field/id "fld3"}]))))
