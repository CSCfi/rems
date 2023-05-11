(ns rems.kaocha-circleci
  "Kaocha plugin that performs runtime test filtering in CircleCI using test splitting.
   Skips excluded test ids (not in split test batch) before test run.

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

(defmethod p/-register :rems.kaocha-circleci/plugin [_name plugins]
  (conj plugins
        {:kaocha.hooks/post-load
         (fn [test-plan] ; skip tests that are not included in set of test ids returned by circle ci
           (let [ids (split-test-ids test-plan)]
             (walk-kaocha-tests
              test-plan
              #(assoc-some % :kaocha.testable/skip (when-not (:kaocha.testable/skip %)
                                                     (is-excluded % ids))))))

         :kaocha.hooks/post-test
         (fn [test _test-plan] ; remove skipped tests so that kaocha-junit-xml does not count them again
           (walk-kaocha-tests test #(when-not (:kaocha.testable/skip %)
                                      %)))}))

