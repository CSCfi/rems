(ns rems.circle-ci
  "Kaocha plugin that allows filtering created test plan before execution with testable ids.
   Allows using CircleCI test splitting for parallel tests, e.g. slow browser tests.

   Heavily inspired by https://andreacrotti.github.io/2020-07-28-parallel-ci-kaocha/"
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            [kaocha.plugin :as p]
            [kaocha.testable :refer [test-seq]]
            [medley.core :refer [assoc-some update-existing]]))

(def is-kaocha-var (comp #{:kaocha.type/var} :kaocha.testable/type))

(defn parse-keyword [s]
  (cond-> s
    (str/starts-with? s ":") (subs 1)
    :always (keyword)))

(defn split-test-ids [test-plan]
  (->> (sh/sh "circleci" "tests" "split" "--split-by=timings"
              :in (str/join "\n" (for [test (test-seq test-plan)
                                       :when (is-kaocha-var test)]
                                   (:kaocha.testable/id test))))
       :out
       (str/split-lines)
       (into #{} (map parse-keyword))))

(defn walk-kaocha-tests [m f]
  (letfn [(recurse [tests]
            (keep #(walk-kaocha-tests % f) tests))]
    (some-> m
            (f)
            (update-existing :kaocha/tests recurse)
            (update-existing :kaocha.test-plan/tests recurse)
            (update-existing :kaocha.result/tests recurse))))

(defn is-excluded [test test-ids]
  (when (is-kaocha-var test)
    (when-some [id (:kaocha.testable/id test)]
      (not (contains? test-ids id)))))

(defn skip-excluded-testables [test-plan test-ids]
  (-> test-plan
      (walk-kaocha-tests (fn [suite]
                           (if-not (:kaocha.testable/skip suite)
                             (assoc-some suite :kaocha.testable/skip (is-excluded suite test-ids))
                             suite)))))

(defn remove-skipped-testables [test]
  (-> test
      (walk-kaocha-tests (fn [suite]
                           (when-not (:kaocha.testable/skip suite)
                             suite)))))

(defmethod p/-register :rems.circle-ci/plugin [_name plugins]
  (conj plugins
        {:kaocha.hooks/post-load
         (fn [test-plan] ; skip tests that are not included in set of test ids returned by circle ci
           (skip-excluded-testables test-plan (split-test-ids test-plan)))

         :kaocha.hooks/post-test
         (fn [test _test-plan] ; remove skipped tests so that kaocha-junit-xml does not count them again
           (remove-skipped-testables test))}))

