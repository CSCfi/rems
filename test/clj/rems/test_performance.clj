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
            [rems.db.user-settings]
            [rems.db.test-data-users :refer [+fake-users+ +fake-user-data+]]
            [rems.email.template]
            [rems.locales]
            [rems.markdown]
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
                   (mount/stop #'rems.db.applications/all-applications-cache
                               #'rems.db.events/low-level-events-cache
                               #'rems.db.user-settings/low-level-user-settings-cache))
        cached (fn []
                 (mount/stop #'rems.db.applications/all-applications-cache #'rems.db.events/low-level-events-cache #'rems.db.user-settings/low-level-user-settings-cache)
                 (mount/start #'rems.db.applications/all-applications-cache #'rems.db.events/low-level-events-cache #'rems.db.user-settings/low-level-user-settings-cache)
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

(defn- get-template-app-and-event [handler-count]
  (let [fake-handlers (for [n (range handler-count)
                            :let [userid (str "benchmark-handler-" n)]]
                        [userid {:userid userid
                                 :name (str "Benchmark handler " n)
                                 :email "fake.handler.email@rems.performance.test"}])
        application {:application/id 7
                     :application/external-id "2024-05-15/1"
                     :application/applicant (get +fake-user-data+ (:alice +fake-users+))
                     :application/description "Template benchmark"
                     :application/resources [{:catalogue-item/title {:en "Benchmark resource (EN)"
                                                                     :fi "Benchmark resource (FI)"
                                                                     :sv "Benchmark resource (SV)"}}]
                     :application/workflow {:workflow.dynamic/handlers (set (map first fake-handlers))}}
        decided (-> {:application/id 7
                     :event/type :application.event/decided
                     :event/actor (:decider +fake-users+)
                     :application/decision :approved}
                    (rems.application.model/enrich-event (into +fake-user-data+ fake-handlers) nil))]
    {:application application
     :event decided}))

(defn benchmark-template-performance [{:keys [benchmark-handlers]}]
  (let [setup-benchmark (fn setup-benchmark-f []
                          (mount/start #'rems.config/env ; :enable-handler-emails is enabled by default
                                       #'rems.locales/translations)
                          (rems.text/reset-cached-tr!))
        get-benchmark-fn (fn [handler-count]
                           (let [{:keys [application event]} (get-template-app-and-event handler-count)]
                             (fn benchmark-event-to-emails []
                               (doall (rems.email.template/event-to-emails event application)))))
        all-stats (atom nil)]

    ;; Execution time mean : 9,400171 ms (2019 Macbook Pro with 2,3 GHz 8-Core Intel Core i9)
    ;; translations cache size 882,5 KiB
    (with-redefs [rems.db.user-settings/get-user-settings (fn [& _] {:language (rand-nth [:en :fi :sv])})]
      (doall
       (for [n benchmark-handlers
             :let [test-event-to-emails (get-benchmark-fn n)
                   stats (run-benchmark {:name (format "event-to-emails, cache, %s handlers" n)
                                         :benchmark test-event-to-emails
                                         :setup setup-benchmark})
                   cache-size (mm/measure rems.text/cached-tr)]]
         #_(prof/profile (dotimes [_ 100] (test-event-to-emails)))
         (swap! all-stats update :cached (fnil conj []) (merge stats {:handler-count n
                                                                      :cache-size cache-size})))))

    ;; XXX: potentially very slow and interesting only for comparison with cached version (default).
    ;; translations are cached using taoensso.tempura/new-tr-fn because (big & nested) dictionary compilation is expensive.
    ;; Execution time mean : 870,499348 ms (2019 Macbook Pro with 2,3 GHz 8-Core Intel Core i9)
    (let [get-cached-tr rems.tempura/get-cached-tr]
      (with-redefs [rems.db.user-settings/get-user-settings (fn [& _] {:language (rand-nth [:en :fi :sv])})
                    rems.tempura/get-cached-tr #(get-cached-tr % {:cache-dict? false})]
        (doall
         (for [n benchmark-handlers
               :let [test-event-to-emails (get-benchmark-fn n)
                     stats (run-benchmark {:name (format "event-to-emails, disabled cache, %s handlers" n)
                                           :benchmark test-event-to-emails
                                           :setup setup-benchmark})]]
           #_(prof/profile (dotimes [_ 10] (test-event-to-emails)))
           (swap! all-stats update :disabled (fnil conj []) (merge stats {:handler-count n}))))))
    @all-stats))

(defn- print-template-performance-tables [all-stats]
  (let [format-time #(apply criterium/format-value % (criterium/scale-time %))
        mean (comp format-time :mean)
        low (comp format-time :low)
        high (comp format-time :high)]
    (when (:cached all-stats)
      (println "")
      (println "event-to-emails, cache")
      (println "---")
      (doseq [row (rems.markdown/markdown-table
                   {:header ["handler count" "mean" "lower-q 2.5%" "upper-q 97.5%" "cache size"]
                    :rows (->> (:cached all-stats)
                               (mapv (juxt :handler-count mean low high :cache-size)))})]
        (println row)))
    (when (:disabled all-stats)
      (println "")
      (println "event-to-emails, disabled cache")
      (println "---")
      (doseq [row (rems.markdown/markdown-table
                   {:header ["handler count" "mean" "lower-q 2.5%" "upper-q 97.5%"]
                    :rows (->> (:disabled all-stats)
                               (mapv (juxt :handler-count mean low high)))})]
        (println row)))))

(comment
  (benchmark-get-events)
  (benchmark-get-all-applications)
  (benchmark-get-application)

  ;; handler count increases email count linearly
  (def stats (benchmark-template-performance {:benchmark-handlers [1 10 50 100 200]}))
  ;; additional table formatting
  (print-template-performance-tables stats)

  (prof/clear-results)
  (prof/serve-ui 8080))
