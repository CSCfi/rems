(ns rems.test-performance
  (:require [clj-async-profiler.core :as prof]
            [clj-memory-meter.core :as mm]
            [clojure.string :as str]
            [criterium.core :as criterium]
            [medley.core :refer [map-vals]]
            [mount.core :as mount]
            [rems.application.model]
            [rems.common.util :refer [recursive-keys to-keyword]]
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

(defn- get-cache-sizes []
  (doall
   (for [s ['rems.db.applications/all-applications-cache
            'rems.db.events/event-cache
            'rems.db.user-settings/user-settings-cache
            'rems.db.category/categories-cache
            'rems.db.catalogue/cached
            'rems.db.user-mappings/user-mappings-by-value
            'rems.ext.duo/code-by-id
            'rems.ext.mondo/code-by-id
            'rems.ext.mondo/codes-dag
            'rems.service.dependencies/dependencies-cache
            'rems.text/cached-tr]
         :let [size (mm/measure (-> s requiring-resolve var-get)
                                {:bytes true})]]
     {:name (str s)
      :bytes size
      :size ((var-get #'mm/convert-to-human-readable) size)})))

(def vocabulary (-> "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Ut non diam vel erat dapibus facilisis vel vitae nunc. Curabitur at fermentum lorem. Cras et bibendum ante. Etiam convallis erat justo. Phasellus cursus molestie vehicula. Etiam molestie tellus vitae consectetur dignissim. Pellentesque euismod hendrerit mi sed tincidunt. Integer quis lorem ut ipsum egestas hendrerit. Aenean est nunc, mattis euismod erat in, sodales rutrum mauris. Praesent sit amet risus quis felis congue ultricies. Nulla facilisi. Sed mollis justo id tristique volutpat.\n\nPhasellus augue mi, facilisis ac velit et, pharetra tristique nunc. Pellentesque eget arcu quam. Curabitur dictum nulla varius hendrerit varius. Proin vulputate, ex lacinia commodo varius, ipsum velit viverra est, eget molestie dui nisi non eros. Nulla lobortis odio a magna mollis placerat. Interdum et malesuada fames ac ante ipsum primis in faucibus. Integer consectetur libero ut gravida ullamcorper. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Donec aliquam feugiat mollis. Quisque massa lacus, efficitur vel justo vel, elementum mollis magna. Maecenas at sem sem. Praesent sed ex mattis, egestas dui non, volutpat lorem. Nulla tempor, nisi rutrum accumsan varius, tellus elit faucibus nulla, vel mattis lacus justo at ante. Sed ut mollis ex, sed tincidunt ex.\n\nMauris laoreet nibh eget erat tincidunt pharetra. Aenean sagittis maximus consectetur. Curabitur interdum nibh sed tincidunt finibus. Sed blandit nec lorem at iaculis. Morbi non augue nec tortor hendrerit mollis ut non arcu. Suspendisse maximus nec ligula a efficitur. Etiam ultrices rhoncus leo quis dapibus. Integer vel rhoncus est. Integer blandit varius auctor. Vestibulum suscipit suscipit risus, sit amet venenatis lacus iaculis a. Duis eu turpis sit amet nibh sagittis convallis at quis ligula. Sed eget justo quis risus iaculis lacinia vitae a justo. In hac habitasse platea dictumst. Maecenas euismod et lorem vel viverra.\n\nDonec bibendum nec ipsum in volutpat. Vivamus in elit venenatis, venenatis libero ac, ultrices dolor. Morbi quis odio in neque consequat rutrum. Suspendisse quis sapien id sapien fermentum dignissim. Nam eu est vel risus volutpat mollis sed quis eros. Proin leo nulla, dictum id hendrerit vitae, scelerisque in elit. Proin consectetur sodales arcu ac tristique. Suspendisse ut elementum ligula, at rhoncus mauris. Aliquam lacinia at diam eget mattis. Phasellus quam leo, hendrerit sit amet mi eget, porttitor aliquet velit. Proin turpis ante, consequat in enim nec, tempus consequat magna. Vestibulum fringilla ac turpis nec malesuada. Proin id lectus iaculis, suscipit erat at, volutpat turpis. In quis faucibus elit, ut maximus nibh. Sed egestas egestas dolor.\n\nNulla varius orci quam, id auctor enim ultrices nec. Morbi et tellus ac metus sodales convallis sed vehicula neque. Pellentesque rhoncus mattis massa a bibendum. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Fusce tincidunt nulla non aliquet facilisis. Praesent nisl nisi, finibus id odio sed, consectetur feugiat mauris. Suspendisse sed lacinia ligula. Duis vitae nisl leo. Donec erat arcu, feugiat sit amet sagittis ac, scelerisque nec est. Pellentesque finibus mauris nulla, in maximus sapien pharetra vitae. Sed leo elit, consequat eu aliquam vitae, feugiat ut eros. Pellentesque dictum feugiat odio sed commodo. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin neque quam, varius vel libero sit amet, rhoncus sollicitudin ex. In a dui non neque malesuada pellentesque.\n\nProin tincidunt nisl non commodo faucibus. Sed porttitor arcu neque, vitae bibendum sapien placerat nec. Integer eget tristique orci. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Donec eu molestie eros. Nunc iaculis rhoncus enim, vel mattis felis fringilla condimentum. Interdum et malesuada fames ac ante ipsum primis in faucibus. Aenean ac augue nulla. Phasellus vitae nulla lobortis, mattis magna ac, gravida ipsum. Aenean ornare non nunc non luctus. Aenean lacinia lectus nec velit finibus egestas vel ut ipsum. Cras hendrerit rhoncus erat, vel maximus nunc.\n\nPraesent quis imperdiet quam. Praesent ligula tellus, consectetur sed lacus eu, malesuada condimentum tellus. Donec et diam hendrerit, dictum diam quis, aliquet purus. Suspendisse pulvinar neque at efficitur iaculis. Nulla erat orci, euismod id velit sed, dictum hendrerit arcu. Nulla aliquam molestie aliquam. Duis et semper nisi, eget commodo arcu. Praesent rhoncus, nulla id sodales eleifend, ante ipsum pellentesque augue, id iaculis sem est vitae est. Phasellus cursus diam a lorem vestibulum sodales. Nullam lacinia tortor vel tellus commodo, sit amet sodales quam malesuada.\n\nNulla tempor lectus vel arcu feugiat, vel dapibus ex dapibus. Maecenas purus justo, aliquet et sem sit amet, tincidunt venenatis dui. Nulla eget purus id sapien elementum rutrum eu vel libero. Cras non accumsan justo posuere.\n\n"
                    str/lower-case
                    (str/split #"[( \n)+]")
                    distinct
                    sort
                    rest))
(defn get-random-word [& _] (rand-nth vocabulary))

(defn- get-all-translations []
  (let [key-paths (recursive-keys rems.locales/translations)

        find-tr-args (fn [f x]
                       (->> (flatten [x])
                            (into [] (comp (filter string?)
                                           (mapcat f)
                                           (distinct)))))
        vec-args (partial re-seq rems.tempura/+vector-args+)
        map-args #(map (comp keyword second) (re-seq rems.tempura/+map-args+ %))

        vec-arg-translations (for [ks key-paths
                                   :let [v (get-in rems.locales/translations ks)
                                         args (find-tr-args vec-args v)]
                                   :when (seq args)]
                               {:ks ks
                                :lang (first ks)
                                :k (to-keyword (rest ks))
                                :v v
                                :args args
                                :fn-args (mapv get-random-word args)})
        map-arg-translations (for [ks key-paths
                                   :let [v (get-in rems.locales/translations ks)
                                         args (find-tr-args map-args v)]
                                   :when (seq args)]
                               {:ks ks
                                :lang (first ks)
                                :k (to-keyword (rest ks))
                                :v v
                                :args args
                                :fn-args [(reduce #(assoc %1 %2 (get-random-word)) {} args)]})]
    {:vector-args vec-arg-translations
     :map-args map-arg-translations
     :no-args (for [ks (->> key-paths
                            (remove (set (map :ks vec-arg-translations)))
                            (remove (set (map :ks map-arg-translations))))
                    :let [v (get-in rems.locales/translations ks)]]
                {:lang (first ks)
                 :k (to-keyword (rest ks))
                 :v v})}))

(defn- benchmark-all-translations []
  (let [all-translations (get-all-translations)
        tr-all (fn [lang]
                 (rems.text/with-language lang
                   (doseq [t (:no-args all-translations)
                           :when (= lang (:lang t))]
                     (rems.text/text (:k t)))
                   (doseq [t (:vector-args all-translations)
                           :when (= lang (:lang t))]
                     (apply rems.text/text-format (:k t) (:fn-args t)))
                   (doseq [t (:map-args all-translations)
                           :when (= lang (:lang t))]
                     (apply rems.text/text-format-map (:k t) (:fn-args t)))))

        print-cache #(println :before "tr cache" (mm/measure rems.text/cached-tr))]

    (mount/start #'rems.config/env ; :enable-handler-emails is enabled by default
                 #'rems.locales/translations)
    (rems.text/reset-cached-tr!)

    (run-benchmarks [{:name "all translations (en), cached" :setup print-cache :benchmark #(tr-all :en)}
                     {:name "all translations (fi), cached" :setup print-cache :benchmark #(tr-all :fi)}
                     {:name "all translations (sv), cached" :setup print-cache :benchmark #(tr-all :sv)}])

    (println :after "tr cache" (mm/measure rems.text/cached-tr))))

(comment
  (benchmark-get-events)
  (benchmark-get-all-applications)
  (benchmark-get-application)

  ;; handler count increases email count linearly
  (def stats (benchmark-template-performance {:benchmark-handlers [1 10 50 100 200]}))
  ;; additional table formatting
  (print-template-performance-tables stats)

  (benchmark-all-translations)

  (def cache-stats (get-cache-sizes))
  ;; additional table formatting
  (do (println "")
      (println "cache sizes")
      (println "---")
      (doseq [row (rems.markdown/markdown-table
                   {:header ["cache" "size"]
                    :rows (->> cache-stats
                               (sort-by :bytes >)
                               (mapv (juxt :name :size)))})]
        (println row)))

  (prof/clear-results)
  (prof/serve-ui 8080))
