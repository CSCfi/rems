(ns rems.circle-ci
  "Kaocha plugin that allows filtering created test plan before execution with testable ids.
   Allows using CircleCI test splitting for parallel tests, e.g. slow browser tests.

   Heavily inspired by https://andreacrotti.github.io/2020-07-28-parallel-ci-kaocha/"
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            [kaocha.plugin :as p]
            [kaocha.testable :refer [test-seq]]
            [medley.core :refer [assoc-some update-existing]]))

(def test-ids (atom #{}))

(defn get-test-ids [test]
  (->> (test-seq test)
       (sequence (comp (filter (comp #{:kaocha.type/var} :kaocha.testable/type))
                       (map :kaocha.testable/id)))))

(defn parse-keyword [s]
  (cond-> s
    (str/starts-with? s ":") (subs 1)
    :always (keyword)))

(defn get-split-test-ids! [test-plan]
  (->> (sh/sh "circleci" "tests" "split" "--split-by=timings"
              :in (str/join "\n" (get-test-ids test-plan)))
       :out
       (str/split-lines)
       (into #{} (map parse-keyword))
       (reset! test-ids)))

(defn exclude [test]
  (when-some [id (:kaocha.testable/id test)]
    (not-any? #{id} @test-ids)))

(defn skip-excluded-tests [test]
  (cond
    (:kaocha.testable/skip test)
    test

    (= :kaocha.type/var (:kaocha.testable/type test))
    (-> test
        (assoc-some :kaocha.testable/skip (exclude test)))

    :else
    (-> test
        (update-existing :kaocha.test-plan/tests #(map skip-excluded-tests %)))))

(defn without-skipped-tests [suite]
  (cond
    (:kaocha.testable/skip suite)
    false

    :else
    (-> suite
        (update-existing :kaocha.result/tests #(filter without-skipped-tests %)))))

(defmethod p/-register :rems.circle-ci/plugin [_name plugins]
  (conj plugins
        {:kaocha.hooks/post-load
         (fn [test-plan]
           (get-split-test-ids! test-plan)
           test-plan)

         :kaocha.hooks/pre-test
         (fn [test _test-plan]
           (skip-excluded-tests test))

         :kaocha.hooks/post-test
         (fn [test _test-plan]
           (without-skipped-tests test))}))

