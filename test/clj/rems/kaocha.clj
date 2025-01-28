(ns rems.kaocha
  "Namespace for various Kaocha test runner helpers."
  (:require [better-cond.core :as b]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [kaocha.plugin :as p]
            [kaocha.result]
            [kaocha.testable :refer [test-seq]]
            [medley.core :refer [assoc-some update-existing]]
            [rems.db.testing]
            [rems.markdown]
            [rems.service.caches]))

;; directories for storing plugin output
(def cache-statistics-plugin-dir "target/cache-statistics-plugin")
(def circleci-parallel-plugin-dir "target/circleci-parallel-plugin")

(def is-kaocha-var (comp #{:kaocha.type/var} :kaocha.testable/type))

(defn parse-keyword [s]
  (cond-> s
    (str/starts-with? s ":") (subs 1)
    :always (keyword)))

(defn split-test-ids [test-plan]
  (let [test-ids (for [test (test-seq test-plan)
                       :when (is-kaocha-var test)]
                   (:kaocha.testable/id test))
        test-ids-batch (->> (sh/sh "circleci" "tests" "split" "--split-by=timings" "--timings-type=testname"
                                   :in (str/join "\n" test-ids))
                            :out
                            (str/split-lines)
                            (into #{} (map parse-keyword)))
        test-ids-report-file (io/file circleci-parallel-plugin-dir "test-ids.txt")
        split-test-ids-report-file (io/file circleci-parallel-plugin-dir "split-test-ids.txt")]

    (io/make-parents test-ids-report-file) ; shared parent, need only once
    (spit test-ids-report-file (str/join "\n" (sort test-ids)))
    (spit split-test-ids-report-file (str/join "\n" (sort test-ids-batch)))

    test-ids-batch))

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

;; Plugin that performs runtime test filtering in CircleCI using test splitting.
;; Skips excluded test ids (not in split test batch) before test run.
;; Heavily inspired by https://andreacrotti.github.io/2020-07-28-parallel-ci-kaocha/
(defmethod p/-register :rems.kaocha/circleci-parallel-plugin [_name plugins]
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

(defn- group-cache-stats-by-test [raw-stats]
  (let [cache-ids (into #{} (mapcat keys) (vals raw-stats))]
    (apply merge-with merge (for [id cache-ids
                                  [test-id caches] raw-stats
                                  :let [c (get caches id)]
                                  :when (some? c)]
                              {id {test-id c}}))))

(defn- sum-total [x] (+ (:get x 0) (:reload x 0) (:upsert x 0) (:evict x 0)))

(defn- enrich-statistics [[cache-id by-test-id]]
  (let [total-stats (apply merge-with + (vals by-test-id))
        total (sum-total total-stats)]
    (when-not (zero? total) ; remove uninitialized or unused caches
      {:id cache-id
       :sum-total total
       :total (assoc total-stats
                     :id cache-id
                     :% 100.0)
       :by-total (->> by-test-id
                      (keep (fn [[test-id stats]]
                              (let [test-total (sum-total stats)]
                                (when-not (zero? test-total) ; remove tests that didn't use caches
                                  (assoc stats
                                         :id test-id
                                         :% (* (double (/ test-total total)) 100.0)))))))})))

(def top-n-results 5)

(defn- get-tabular-data [enriched-stats]
  (let [indent-test-id #(assoc % :id (str " > " (:id %)))
        format-percent #(assoc % :% (format "%.2f" (:% %)))]

    (->> (for [cache-stats (sort-by :sum-total > enriched-stats)]
           (cons (format-percent (:total cache-stats))
                 (->> (:by-total cache-stats)
                      (sort-by :% >)
                      (take top-n-results)
                      (map indent-test-id)
                      (map format-percent))))
         (interpose [{}]) ; add blank row between caches so table is not too dense
         (apply concat))))

(defmethod p/-register :rems.kaocha/cache-statistics-plugin [_name plugins]
  (conj plugins
        {:kaocha.hooks/post-summary
         (fn [result]
           (when-not (kaocha.result/failed? result) ; skip performance summary if tests fail
             (b/when-let [stats (->> (rems.db.testing/get-cache-statistics)
                                     group-cache-stats-by-test
                                     (keep enrich-statistics)
                                     seq)
                          report-file (io/file cache-statistics-plugin-dir "cache-report.md")]
               (io/make-parents report-file)
               (spit report-file
                     (str/join "\n"
                               (cons (format "Top %d cache users grouped by cache:\n" top-n-results)
                                     (rems.markdown/markdown-table
                                      {:header [:id "%" :get :upsert :evict :reload]
                                       :rows (get-tabular-data stats)
                                       :row-fn (juxt :id :% :get :upsert :evict :reload)}))))))
           result)}))
