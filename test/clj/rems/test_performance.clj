(ns rems.test-performance
  (:require [clj-async-profiler.core :as prof]
            [clj-memory-meter.core :as mm]
            [criterium.core :as criterium]
            [medley.core :refer [map-vals]]
            [mount.core :as mount]
            [rems.application.model]
            [rems.config]
            [rems.db.applications :as applications]
            [rems.db.events :as events]
            [rems.db.test-data-users :refer [+fake-users+ +fake-user-data+]]
            [rems.email.template]
            [rems.locales]
            [rems.service.todos :as todos]
            [rems.tempura]
            [rems.text])
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
                   (mount/stop #'rems.db.applications/all-applications-cache #'rems.db.events/low-level-events-cache))
        cached (fn []
                 (mount/stop #'rems.db.applications/all-applications-cache #'rems.db.events/low-level-events-cache)
                 (mount/start #'rems.db.applications/all-applications-cache #'rems.db.events/low-level-events-cache)
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

(defn- get-template-app-and-event [{:keys [handler-count]}]
  (let [fake-handlers (for [n (range handler-count)
                            :let [userid (str "benchmark-handler-" n)]]
                        [userid {:userid userid
                                 :name (str "Benchmark handler " n)
                                 :email "fake.handler.email@rems.performance.test"}])
        application {:application/id 7
                     :application/external-id "2024-05-15/1"
                     :application/applicant (get +fake-user-data+ (:alice +fake-users+))
                     :application/description "Template benchmark"
                     :application/resources [{:catalogue-item/title {:en "Benchmark resource"}}]
                     :application/workflow {:workflow.dynamic/handlers (set (map first fake-handlers))}}
        decided (-> {:application/id 7
                     :event/type :application.event/decided
                     :event/actor (:decider +fake-users+)
                     :application/decision :approved}
                    (rems.application.model/enrich-event (into +fake-user-data+ fake-handlers) nil))]
    {:application application
     :event decided}))

(defn benchmark-template-performance []
  (let [{:keys [application event]} (get-template-app-and-event {:handler-count 200})
        setup-benchmark (fn setup-benchmark-f []
                          (mount/start #'rems.config/env ; :enable-handler-emails is enabled by default
                                       #'rems.locales/translations)
                          (rems.text/reset-cached-tr!))
        test-event-to-emails (fn benchmark-event-to-emails []
                               (doall (rems.email.template/event-to-emails event application)))]

    ;; Execution time mean : 9,400171 ms (2019 Macbook Pro with 2,3 GHz 8-Core Intel Core i9)
    ;; translations cache size 882,5 KiB
    (with-redefs [rems.db.user-settings/get-user-settings (fn [& _] {:language (rand-nth [:en :fi :sv])})]
      (run-benchmark {:name "event-to-emails"
                      :benchmark test-event-to-emails
                      :setup setup-benchmark})
      (prof/profile (dotimes [_ 100] (test-event-to-emails)))
      (println "translations cache size" (mm/measure rems.text/cached-tr)))

    ;; XXX: potentially very slow and interesting only for comparison with cached version (default).
    ;; translations are cached using taoensso.tempura/new-tr-fn because (big & nested) dictionary compilation is expensive.
    ;; Execution time mean : 870,499348 ms (2019 Macbook Pro with 2,3 GHz 8-Core Intel Core i9)
    ;; translations cache size 882,0 KiB
    #_(let [get-cached-tr rems.tempura/get-cached-tr]
        (with-redefs [rems.db.user-settings/get-user-settings (fn [& _] {:language (rand-nth [:en :fi :sv])})
                      rems.tempura/get-cached-tr #(get-cached-tr % {:cache-dict? false})]
          (run-benchmark {:name "event-to-emails, no cache"
                          :benchmark test-event-to-emails
                          :setup setup-benchmark})
          (prof/profile (test-event-to-emails))
          (println "translations cache size" (mm/measure rems.text/cached-tr))))))

(comment
  ;; Note: If clj-memory-meter throws InaccessibleObjectException on Java 9+,
  ;;       you *could* work around it by adding `--add-opens` JVM options, but
  ;;       the root cause is probably that there is a lazy sequence that could
  ;;       easily be avoided.
  (benchmark-get-events)
  (benchmark-get-all-applications)
  (benchmark-get-application)
  (benchmark-template-performance)

  (prof/clear-results)
  (prof/serve-ui 8080))
