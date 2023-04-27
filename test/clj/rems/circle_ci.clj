(ns rems.circle-ci
  "Kaocha plugin that allows filtering created test plan before execution with testable ids.
   Allows using CircleCI test splitting for parallel tests, e.g. slow browser tests.

   Heavily inspired by https://andreacrotti.github.io/2020-07-28-parallel-ci-kaocha/"
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            [kaocha.plugin :as p]
            [kaocha.testable :refer [test-seq]]
            [medley.core :refer [update-existing]]))

(defn get-test-ids [test-plan]
  (->> (test-seq test-plan)
       (sequence (comp (mapcat :kaocha.test-plan/tests)
                       (remove :kaocha.testable/skip)
                       (filter (comp #{:kaocha.type/var} :kaocha.testable/type))
                       (map :kaocha.testable/id)))))

(defn split-test-ids [test-ids]
  (->> (sh/sh "circleci" "tests" "split" "--split-by=timings"
              :in (str/join "\n" test-ids))
       :out
       (str/split-lines)
       (into #{} (map #(keyword (subs % 1)))))) ; str/join turns :a/b into ":a/b", but keyword prepends ":", so remove first

(defn skip-excluded-testables [test test-ids]
  (if (:kaocha.testable/skip test)
    test
    (case (:kaocha.testable/type test)
      (:kaocha.type/clojure.test
       :kaocha.type/ns)
      (update-existing test :kaocha.test-plan/tests (partial mapv #(skip-excluded-testables % test-ids)))

      :kaocha.type/var
      (let [excluded? (not (contains? test-ids (:kaocha.testable/id test)))]
        (assoc test :kaocha.testable/skip excluded?)))))

(defmethod p/-register :rems.circle-ci/plugin [_name plugins]
  (conj plugins
        {:kaocha.hooks/post-load
         (fn [test-plan]
           (assoc test-plan ::test-ids (-> test-plan
                                           (get-test-ids)
                                           (split-test-ids))))

         :kaocha.hooks/pre-test
         (fn [test test-plan]
           (skip-excluded-testables test (::test-ids test-plan)))}))

