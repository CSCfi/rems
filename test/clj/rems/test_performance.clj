(ns rems.test-performance
  (:require [clj-memory-meter.core :as mm]
            [criterium.core :as criterium]
            [medley.core :refer [map-vals]]
            [mount.core :as mount]
            [rems.api.applications-v2 :as applications-v2]
            [rems.api.reviews :as reviews]
            [rems.db.dynamic-roles :as dynamic-roles])
  (:import [java.util Locale]))

(defn run-benchmark [benchmark]
  (println "\n=====" (:name benchmark) "=====")
  ((:setup benchmark))
  (let [result (criterium/with-progress-reporting
                 (criterium/quick-benchmark ((:benchmark benchmark)) {}))]
    (criterium/report-result result)
    ;(clojure.pprint/pprint (dissoc result :results))
    {:name (:name benchmark)
     :mean (first (:mean result))
     :high (first (:upper-q result))
     :low (first (:lower-q result))}))

(defn run-benchmarks [benchmarks]
  (let [results (doall (for [benchmark benchmarks]
                         (run-benchmark benchmark)))
        longest-name (->> results (map :name) (map count) (apply max))
        right-pad (fn [s length]
                    (apply str s (repeat (- length (count s)) " ")))]
    (println "\n===== Summary =====")
    (doseq [result results]
      (println (right-pad (:name result) longest-name)
               (->> (select-keys result [:low :mean :high])
                    (map-vals #(String/format Locale/ENGLISH "%.3f ms" (to-array [(* 1000 %)]))))))))

(defn benchmark-get-all-applications []
  (let [test-get-all-unrestricted-applications #(doall (applications-v2/get-all-unrestricted-applications))
        test-get-all-applications #(doall (applications-v2/get-all-applications "alice"))
        test-get-own-applications #(doall (applications-v2/get-own-applications "alice"))
        ;; developer can view much more applications than alice, so it takes longer to filter reviews from all apps
        test-get-open-reviews #(doall (reviews/get-open-reviews "developer"))
        no-cache (fn []
                   (mount/stop #'applications-v2/all-applications-cache))
        cached (fn []
                 (mount/stop #'applications-v2/all-applications-cache)
                 (mount/start #'applications-v2/all-applications-cache)
                 (test-get-all-unrestricted-applications))]
    (run-benchmarks [{:name "get-all-unrestricted-applications, no cache"
                      :setup no-cache
                      :benchmark test-get-all-unrestricted-applications}
                     {:name "get-all-unrestricted-applications, cached"
                      :setup cached
                      :benchmark test-get-all-unrestricted-applications}
                     {:name "get-all-applications, cached"
                      :setup cached
                      :benchmark test-get-all-applications}
                     {:name "get-own-applications, cached"
                      :setup cached
                      :benchmark test-get-own-applications}
                     {:name "get-open-reviews, cached"
                      :setup cached
                      :benchmark test-get-open-reviews}])
    (println "cache size" (mm/measure applications-v2/all-applications-cache))))

(defn benchmark-get-application []
  (let [test-get-application #(applications-v2/get-application "developer" 12)
        no-cache (fn []
                   (mount/stop #'applications-v2/application-cache))
        cached (fn []
                 (mount/stop #'applications-v2/application-cache)
                 (mount/start #'applications-v2/application-cache)
                 (test-get-application))]
    (run-benchmarks [{:name "get-application, no cache"
                      :setup no-cache
                      :benchmark test-get-application}
                     {:name "get-application, cached"
                      :setup cached
                      :benchmark test-get-application}])
    (println "cache size" (mm/measure applications-v2/application-cache))))

(defn benchmark-get-dynamic-roles []
  (let [test-get-roles #(doall (dynamic-roles/get-roles "developer"))
        no-cache (fn []
                   (mount/stop #'dynamic-roles/dynamic-roles-cache))
        cached (fn []
                 (mount/stop #'dynamic-roles/dynamic-roles-cache)
                 (mount/start #'dynamic-roles/dynamic-roles-cache)
                 (test-get-roles))]
    (run-benchmarks [{:name "get-roles, no cache"
                      :setup no-cache
                      :benchmark test-get-roles}
                     {:name "get-roles, cached"
                      :setup cached
                      :benchmark test-get-roles}])
    (println "cache size" (mm/measure dynamic-roles/dynamic-roles-cache))))

(comment
  ;; Note: If clj-memory-meter throws InaccessibleObjectException on Java 9+,
  ;;       you *could* work around it by adding `--add-opens` JVM options, but
  ;;       the root cause is probably that there is a lazy sequence that could
  ;;       easily be avoided.
  (benchmark-get-all-applications)
  (benchmark-get-application)
  (benchmark-get-dynamic-roles))
