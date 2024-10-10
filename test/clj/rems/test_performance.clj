(ns rems.test-performance
  (:require [clj-async-profiler.core :as prof]
            [clj-memory-meter.core :as mm]
            [criterium.core :as criterium]
            [medley.core :refer [map-vals]]
            [mount.core :as mount]
            [rems.application.model]
            [rems.common.util :refer [recursive-keys to-keyword]]
            [rems.config]
            [rems.db.applications]
            [rems.db.events]
            [rems.db.user-settings]
            [rems.db.test-data-users :refer [+fake-users+ +fake-user-data+]]
            [rems.email.template]
            [rems.locales]
            [rems.markdown]
            [rems.service.application]
            [rems.service.caches]
            [rems.service.test-data :as test-data]
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
  (let [last-event-id (:event/id (last (rems.db.events/get-all-events-since 0)))
        test-get-all-events-since-beginning #(doall (rems.db.events/get-all-events-since 0))
        test-get-all-events-since-end #(doall (rems.db.events/get-all-events-since last-event-id))
        test-get-application-events #(doall (rems.db.events/get-application-events 12))]
    (run-benchmarks [{:name "get-all-events-since, all events"
                      :benchmark test-get-all-events-since-beginning}
                     {:name "get-all-events-since, zero new events"
                      :benchmark test-get-all-events-since-end}
                     {:name "get-application-events"
                      :benchmark test-get-application-events}])))

(defn benchmark-get-all-applications []
  (let [test-get-all-unrestricted-applications #(doall (rems.db.applications/get-all-unrestricted-applications))
        test-get-all-applications #(doall (rems.db.applications/get-all-applications-full "alice"))
        test-get-all-application-roles #(doall (rems.db.applications/get-all-application-roles "developer"))
        test-get-my-applications #(doall (rems.db.applications/get-my-applications-full "alice"))
        ;; developer can view much more applications than alice, so it takes longer to filter reviews from all apps
        test-get-todos #(doall (rems.service.application/get-todos "developer"))
        no-cache (fn []
                   (mount/stop #'rems.db.applications/all-applications-cache
                               #'rems.db.events/low-level-events-cache)
                   (rems.service.caches/reset-all-caches!))
        cached (fn []
                 (mount/stop #'rems.db.applications/all-applications-cache #'rems.db.events/low-level-events-cache)
                 (mount/start #'rems.db.applications/all-applications-cache #'rems.db.events/low-level-events-cache)
                 (rems.service.caches/start-all-caches!)
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
    (println "cache size" (mm/measure rems.db.applications/all-applications-cache))))

(defn benchmark-get-application []
  (let [test-get-application #(rems.db.applications/get-application-for-user "developer" 12)]
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
                    (rems.application.model/enrich-event (into +fake-user-data+ fake-handlers) nil nil))]
    {:application application
     :event decided}))

(defn benchmark-template-performance [{:keys [benchmark-handlers]}]
  (let [setup-benchmark (fn setup-benchmark-f []
                          (mount/start #'rems.config/env ; :enable-handler-emails is enabled by default
                                       #'rems.locales/translations)
                          (rems.text/reset-cached-tr!))
        get-benchmark-fn (fn get-benchmark-event-to-emails [handler-count]
                           (let [{:keys [application event]} (get-template-app-and-event handler-count)]
                             (fn benchmark-event-to-emails []
                               (doall (rems.email.template/event-to-emails event application)))))
        all-stats (atom nil)]

    ;; Execution time mean : 9,400171 ms (2019 Macbook Pro with 2,3 GHz 8-Core Intel Core i9)
    ;; translations cache size 882,5 KiB
    (with-redefs [rems.db.user-settings/get-user-settings (fn wrapped-get-user-settings [& _]
                                                            {:language (rand-nth [:en :fi :sv])})]
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
      (with-redefs [rems.db.user-settings/get-user-settings (fn wrapped-get-user-settings [& _]
                                                              {:language (rand-nth [:en :fi :sv])})
                    rems.tempura/get-cached-tr (fn wrapped-get-cached-tr [translations & _]
                                                 (get-cached-tr translations {:cache-dict? false
                                                                              :cache-locales? false}))]
        (doall
         (for [n benchmark-handlers
               :let [test-event-to-emails (get-benchmark-fn n)
                     stats (run-benchmark {:name (format "event-to-emails, disabled cache, %s handlers" n)
                                           :benchmark test-event-to-emails
                                           :setup setup-benchmark})]]
           #_(prof/profile (dotimes [_ 10] (test-event-to-emails)))
           (swap! all-stats update :disabled (fnil conj []) (merge stats {:handler-count n}))))))
    @all-stats))

(defn- format-criterium-time [x]
  (apply criterium/format-value x (criterium/scale-time x)))

(defn- print-template-performance-tables [all-stats]
  (let [mean (comp format-criterium-time :mean)
        low (comp format-criterium-time :low)
        high (comp format-criterium-time :high)]
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

(defn- format-memory-size [size]
  ((var-get #'mm/convert-to-human-readable) size))

(defn- get-cache-sizes []
  (doall
   (concat (for [c (rems.service.caches/get-all-caches)
                 :let [size (mm/measure c {:bytes true})]]
             {:name (:id c "unknown cache?")
              :bytes size
              :size (format-memory-size size)})

           (for [s ['rems.db.applications/all-applications-cache
                    'rems.db.events/event-cache
                    'rems.ext.duo/code-by-id
                    'rems.ext.mondo/code-by-id
                    'rems.ext.mondo/codes-dag
                    'rems.text/cached-tr]
                 :let [size (mm/measure (var-get (requiring-resolve s)) {:bytes true})]]
             {:name s
              :bytes size
              :size (format-memory-size size)}))))

(defn- get-all-translations []
  (let [key-paths (recursive-keys rems.locales/translations)
        vec-arg-translations (for [ks key-paths
                                   :let [v (get-in rems.locales/translations ks)
                                         args (rems.tempura/find-vec-params v)]
                                   :when (seq args)]
                               {:ks ks :args (distinct args)})
        map-arg-translations (for [ks key-paths
                                   :let [v (get-in rems.locales/translations ks)
                                         args (rems.tempura/find-map-params v)]
                                   :when (seq args)]
                               {:ks ks :args (distinct args)})
        no-arg-translations (for [ks (->> key-paths
                                          (remove (set (map :ks vec-arg-translations)))
                                          (remove (set (map :ks map-arg-translations))))]
                              {:ks ks})]
    {:vec-args vec-arg-translations
     :map-args map-arg-translations
     :no-args no-arg-translations}))

(defn- benchmark-all-translations []
  (let [all-translations (get-all-translations)
        vec-args (group-by :lang (for [t (:vec-args all-translations)]
                                   {:lang (first (:ks t))
                                    :key (to-keyword (rest (:ks t)))
                                    :fn-args (mapv (fn [& _] (test-data/random-word)) (:args t))}))
        map-args (group-by :lang (for [t (:map-args all-translations)]
                                   {:lang (first (:ks t))
                                    :key (to-keyword (rest (:ks t)))
                                    :fn-args [(reduce #(assoc %1 %2 (test-data/random-word)) {} (:args t))]}))
        no-args (group-by :lang (for [t (:no-args all-translations)]
                                  {:lang (first (:ks t))
                                   :key (to-keyword (rest (:ks t)))}))
        text #(rems.text/text (:key %))
        text-format #(apply rems.text/text-format (:key %) (:fn-args %))
        tr-all (fn []
                 (doseq [lang [:en :fi :sv]]
                   (rems.text/with-language lang
                     (mapv text (get no-args lang))
                     (mapv text-format (get vec-args lang))
                     (mapv text-format (get map-args lang)))))]

    (mount/start #'rems.config/env ; :enable-handler-emails is enabled by default
                 #'rems.locales/translations)
    (rems.text/reset-cached-tr!)

    (let [empty-cache-size (mm/measure rems.text/cached-tr)
          _ (doall (tr-all)) ; run once to warm cache
          warm-cache-size (mm/measure rems.text/cached-tr)
          all (run-benchmark {:name "all translations" :benchmark tr-all})
          final-cache-size (mm/measure rems.text/cached-tr)]

      {:all all
       :empty-cache-size empty-cache-size
       :warm-cache-size warm-cache-size
       :final-cache-size final-cache-size
       :translations-count (->> (vals all-translations)
                                (map count)
                                (reduce + 0))})))

(comment
  (benchmark-get-events)
  (benchmark-get-all-applications)
  (benchmark-get-application)

  ;; handler count increases email count linearly
  (def template-stats (benchmark-template-performance {:benchmark-handlers [1 10 50 100 200]}))
  ;; additional table formatting
  (print-template-performance-tables template-stats)

  (def translations-stats (benchmark-all-translations))
  ;; additional table formatting
  (do (println "")
      (println "translations")
      (println "---")
      (doseq [row (rems.markdown/markdown-table
                   {:header ["total translations" "mean" "empty cache size" "warm cache size" "final cache size"]
                    :rows (->> [translations-stats]
                               (mapv (juxt :translations-count
                                           (comp format-criterium-time :mean :all)
                                           :empty-cache-size
                                           :warm-cache-size
                                           :final-cache-size)))})]
        (println row)))

  (def cache-stats (get-cache-sizes))
  ;; additional table formatting
  (let [total-size (format-memory-size (reduce + 0 (mapv :bytes cache-stats)))]
    (println "")
    (println "cache sizes")
    (println "---")
    (doseq [row (rems.markdown/markdown-table
                 {:header ["cache" (format "size (total: %s)" total-size) "statistics"]
                  :rows (->> (or cache-stats (get-cache-sizes))
                             (sort-by :bytes >)
                             (mapv (juxt :name :size :statistics)))})]
      (println row)))

  (prof/clear-results)
  (prof/serve-ui 8080))
