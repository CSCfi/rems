(ns rems.test-performance
  (:require [clj-memory-meter.core :as mm]
            [criterium.core :as criterium]
            [medley.core :refer [map-vals]]
            [mount.core :as mount]
            [rems.service.todos :as todos]
            [rems.db.applications :as applications]
            [rems.db.events :as events])
  (:import [java.util Locale]))

(defn run-benchmark [benchmark]
  (println "\n=====" (:name benchmark) "=====")
  (when-let [setup (:setup benchmark)]
    (setup))
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

(defn benchmark-get-events []
  (let [last-event-id (:event/id (last (events/get-all-events-since 0)))
        test-get-all-events-since-beginning #(doall (events/get-all-events-since 0))
        test-get-all-events-since-end #(doall (events/get-all-events-since last-event-id))
        test-get-application-events #(doall (events/get-application-events 12))]
    (run-benchmarks [{:name "get-all-events-since, all events"
                      :benchmark test-get-all-events-since-beginning}
                     {:name "get-all-events-since, zero new events"
                      :benchmark test-get-all-events-since-end}
                     {:name "get-application-events"
                      :benchmark test-get-application-events}])))

(defn benchmark-get-all-applications []
  (let [test-get-all-unrestricted-applications #(doall (applications/get-all-unrestricted-applications))
        test-get-all-applications #(doall (applications/get-all-applications "alice"))
        test-get-all-application-roles #(doall (applications/get-all-application-roles "developer"))
        test-get-my-applications #(doall (applications/get-my-applications "alice"))
        ;; developer can view much more applications than alice, so it takes longer to filter reviews from all apps
        test-get-todos #(doall (todos/get-todos "developer"))
        no-cache (fn []
                   (mount/stop #'applications/all-applications-cache))
        cached (fn []
                 (mount/stop #'applications/all-applications-cache)
                 (mount/start #'applications/all-applications-cache)
                 (test-get-all-unrestricted-applications))]
    (run-benchmarks [{:name "get-all-unrestricted-applications, no cache"
                      :benchmark test-get-all-unrestricted-applications
                      :setup no-cache}
                     {:name "get-all-unrestricted-applications, cached"
                      :benchmark test-get-all-unrestricted-applications
                      :setup cached}
                     {:name "get-all-applications, cached"
                      :benchmark test-get-all-applications
                      :setup cached}
                     {:name "get-all-application-roles, cached"
                      :benchmark test-get-all-application-roles
                      :setup cached}
                     {:name "get-my-applications, cached"
                      :benchmark test-get-my-applications
                      :setup cached}
                     {:name "get-todos, cached"
                      :benchmark test-get-todos
                      :setup cached}])
    (println "cache size" (mm/measure applications/all-applications-cache))))

(defn benchmark-get-application []
  (let [test-get-application #(applications/get-application-for-user "developer" 12)]
    (run-benchmarks [{:name "get-application"
                      :benchmark test-get-application}])))

(comment
  ;; Note: If clj-memory-meter throws InaccessibleObjectException on Java 9+,
  ;;       you *could* work around it by adding `--add-opens` JVM options, but
  ;;       the root cause is probably that there is a lazy sequence that could
  ;;       easily be avoided.
  (benchmark-get-events)
  (benchmark-get-all-applications)
  (benchmark-get-application))
