(ns ^:integration rems.test-performance
  (:require [clj-memory-meter.core :as mm]
            [medley.core :refer [map-vals]]
            [mount.core :as mount]
            [rems.api.applications-v2 :as applications-v2]
            [rems.api.reviews :as reviews]
            [rems.db.dynamic-roles :as dynamic-roles]))

(defn duration-millis [f]
  (let [start (System/nanoTime)
        _ (f)
        end (System/nanoTime)]
    (/ (- end start) 1000000.0)))

(defn run-benchmark [benchmark iterations]
  ((:setup benchmark))
  (doall (for [_ (range iterations)]
           (duration-millis (:benchmark benchmark)))))

(defn statistics [measurements]
  (let [measurements (sort measurements)]
    {:min (first measurements)
     :median (nth measurements (quot (count measurements) 2))
     :max (last measurements)}))

(defn run-benchmarks [iterations benchmarks]
  ;; warmup
  (doseq [benchmark benchmarks]
    (run-benchmark benchmark iterations))
  ;; actual measurements
  (doseq [benchmark benchmarks]
    (let [measurements (run-benchmark benchmark iterations)]
      (println (:name benchmark)
               (->> (statistics measurements)
                    (map-vals #(format "%.3fms" %)))))))

(comment
  (let [test-get-all-unrestricted-applications #(doall (applications-v2/get-all-unrestricted-applications))
        test-get-all-applications #(doall (applications-v2/get-all-applications "alice"))
        test-get-own-applications #(doall (applications-v2/get-own-applications "alice"))
        ;; developer can view much more applications than alice, so it takes longer to filter them
        test-get-open-reviews #(doall (reviews/get-open-reviews "developer"))
        no-cache (fn []
                   (mount/stop #'applications-v2/all-applications-cache))
        cached (fn []
                 (mount/stop #'applications-v2/all-applications-cache)
                 (mount/start #'applications-v2/all-applications-cache)
                 (test-get-all-unrestricted-applications))]
    (run-benchmarks 5 [{:name "get-all-unrestricted-applications, no cache"
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
    (println "cache size" (mm/measure applications-v2/all-applications-cache)))

  (let [test-get-application #(applications-v2/get-application "developer" 12)
        no-cache (fn []
                   (mount/stop #'applications-v2/application-cache))
        cached (fn []
                 (mount/stop #'applications-v2/application-cache)
                 (mount/start #'applications-v2/application-cache)
                 (test-get-application))]
    (run-benchmarks 100 [{:name "get-application, no cache"
                          :setup no-cache
                          :benchmark test-get-application}
                         {:name "get-application, cached"
                          :setup cached
                          :benchmark test-get-application}])
    (println "cache size" (mm/measure applications-v2/application-cache)))

  (let [test-get-roles #(doall (dynamic-roles/get-roles "developer"))
        no-cache (fn []
                   (mount/stop #'dynamic-roles/dynamic-roles-cache))
        cached (fn []
                 (mount/stop #'dynamic-roles/dynamic-roles-cache)
                 (mount/start #'dynamic-roles/dynamic-roles-cache)
                 (test-get-roles))]
    (run-benchmarks 5 [{:name "get-roles, no cache"
                        :setup no-cache
                        :benchmark test-get-roles}
                       {:name "get-roles, cached"
                        :setup cached
                        :benchmark test-get-roles}])
    (println "cache size" (mm/measure dynamic-roles/dynamic-roles-cache))))
