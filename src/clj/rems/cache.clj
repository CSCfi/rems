(ns rems.cache
  (:require [clojure.core.cache.wrapped :as w]
            [clojure.tools.logging.readable :as logr]
            [medley.core :refer [update-existing]]
            [rems.common.dependency :as dep]
            [rems.concurrency :as concurrency]
            [rems.config])
  (:import [java.util.concurrent CountDownLatch TimeUnit]))

(def ^:private caches (atom nil))
(def ^:private caches-dag (atom (dep/make-graph)))

(def ^{:doc "Value that tells cache to skip entry."} absent ::absent)

(def ^:private initial-statistics {:get 0 :reload 0 :upsert 0 :evict 0})

(defprotocol RefreshableCacheProtocol
  "Protocol for cache wrapper that can refresh the underlying cache."

  (reload! [this]
    "Reloads the cache to `(reload-fn)`, or `(reload-fn deps)` if `depends-on` was specified.")
  (ensure-initialized! [this]
    "Reloads the cache if it is not ready.")
  (set-uninitialized! [this]
    "Marks the cache as uninitialized. Next call to ensure-initialized will reload the cache and possible dependencies.")
  (has? [this k]
    "Checks if cache contains `k`.")
  (entries! [this]
    "Retrieves the current cache snapshot.")
  (lookup! [this k] [this k not-found]
    "Retrieves `k` from cache if it exists, or `not-found` if specified.")
  (lookup-or-miss! [this k]
    "Retrieves `k` from cache if it exists, else updates the cache for `k` to `(miss-fn k)` and performs the lookup again.")
  (evict! [this k]
    "Removes `k` from cache.")
  (miss! [this k]
    "Updates the cache for `k` to `(miss-fn k)` and returns the updated value."))

(defprotocol CacheStatisticsProtocol
  "Run-time cache statistics."

  (export-statistics! [this]
    "Retrieves runtime statistics from cache, and resets appropriate counters.")
  (increment-get-statistic! [this]
    "Increments cache access counter.")
  (increment-reload-statistic! [this]
    "Increments cache reload counter.")
  (increment-upsert-statistic! [this]
    "Increments cache insert/update counter.")
  (increment-evict-statistic! [this]
    "Increments cache evict counter."))

(defn get-all-caches [] (some-> @caches vals))

(defn get-cache-dependencies [id]
  (->> (dep/get-dependencies @caches-dag id)
       (map #(get @caches %))))

(def ^:private dependency-loaders-thread-pool (atom nil))

(defn- get-thread-pool! []
  (or @dependency-loaders-thread-pool
      (reset! dependency-loaders-thread-pool (concurrency/work-stealing-thread-pool))))

(defn shutdown-thread-pool! []
  (some-> @dependency-loaders-thread-pool
          (concurrency/shutdown! {:timeout-ms 10000}))
  (reset! dependency-loaders-thread-pool nil))

(defn submit-tasks! [& fns]
  (concurrency/submit! (get-thread-pool!) fns))

(def ^:dynamic ^:private *cache* nil)
(def ^:dynamic ^:private *cache-deps* nil)
(def ^:dynamic ^:private *finished* nil)
(def ^:dynamic ^:private *progress* nil)

(defn- create-join-dependency-task
  "Creates a low-level threaded function that joins `cache` dependency into `cache-deps`.
   Synchronizes via `progress` and `finished` with other threads."
  [cache cache-deps finished progress]
  (binding [*cache* cache
            *cache-deps* cache-deps
            *finished* finished
            *progress* progress]
    (bound-fn []
      (let [w-lock (:write-lock *cache*)
            r-lock (:read-lock *cache*)]
        (locking r-lock ; synchronizes access with other dependency joiners
          (locking w-lock ; ensure cache state is unchanged until this thread finishes
            (let [id (:id *cache*)
                  entries (ensure-initialized! *cache*)]
              (swap! *cache-deps* assoc id entries)
              ;; signal progress to parent thread
              (.countDown *progress*)
              ;; release locks once all dependency joiners are finished
              (.await *finished*))))))))

(defn- reset-dependents-on-change! [id _cache _old-value _new-value]
  (let [cache (get @caches id)
        reset-in-progress? (:reset-in-progress? cache)]
    ;; no need to queue reset while one is still in progress
    (when-not @reset-in-progress?
      (when-let [dependents (seq (dep/get-all-dependents @caches-dag id))]
        (reset! reset-in-progress? true)
        (logr/debug ">" id :reset-dependents {:dependents dependents})
        (doseq [dep-id dependents
                :let [dep-cache (get @caches dep-id)]]
          (set-uninitialized! dep-cache))
        (logr/debug "<" id :reset-dependents)
        (reset! reset-in-progress? false)))))

(defrecord RefreshableCache [id
                             read-lock
                             write-lock
                             ^clojure.lang.IAtom statistics
                             ^clojure.lang.IAtom reset-in-progress?
                             ^clojure.lang.IAtom initialized?
                             ^clojure.lang.IAtom the-cache
                             ^clojure.lang.IFn miss-fn
                             ^clojure.lang.IFn reload-fn]
  CacheStatisticsProtocol

  (export-statistics! [this]
    (let [stats @statistics]
      (reset! statistics (select-keys initial-statistics (keys stats)))
      stats))

  (increment-get-statistic! [this] (swap! statistics update-existing :get inc))
  (increment-reload-statistic! [this] (swap! statistics update :reload inc))
  (increment-upsert-statistic! [this] (swap! statistics update :upsert inc))
  (increment-evict-statistic! [this] (swap! statistics update :evict inc))

  RefreshableCacheProtocol

  (reload! [this]
    (locking read-lock
      (let [deps (get-cache-dependencies id)
            finished (CountDownLatch. 1)]
        (logr/debug ">" id :reload)
        (if (empty? deps)
          (w/seed the-cache (reload-fn))
          ;; XXX: dependency resolver can synchronize immediate dependencies, but
          ;; it does not properly synchronize transitive dependencies (so they might every now and then be off sync).
          ;; this might be workable by e.g. passing latches down.
          (let [progress (CountDownLatch. (count deps))
                cache-deps (atom {})]
            (submit-tasks! (doall
                            (for [c deps]
                              (create-join-dependency-task c cache-deps finished progress))))
            (.await progress 10000 TimeUnit/MILLISECONDS)
            (w/seed the-cache (reload-fn @cache-deps))))

        (let [entries @the-cache]
          (increment-reload-statistic! this)
          (reset! initialized? true)
          (logr/debug "<" id :reload {:count (count entries)})
          (.countDown finished) ; release dependency loaders just before exiting
          @the-cache))))

  (ensure-initialized! [this]
    (increment-get-statistic! this)
    (if @initialized?
      @the-cache
      (reload! this)))

  (set-uninitialized! [this]
    (reset! initialized? false))

  (entries! [this]
    (locking write-lock
      (ensure-initialized! this)))

  (has? [this k]
    (locking write-lock
      (ensure-initialized! this)
      (w/has? the-cache k)))

  (lookup! [this k]
    (locking write-lock
      (ensure-initialized! this)
      (w/lookup the-cache k)))

  (lookup! [this k not-found]
    (locking write-lock
      (ensure-initialized! this)
      (w/lookup the-cache k not-found)))

  (miss! [this k]
    (locking write-lock
      (ensure-initialized! this)

      (let [value (miss-fn k)]
        (if (= absent value)
          (logr/debug id :skip-update k)

          (do
            (logr/debug ">" id :upsert k value)
            (ensure-initialized! this)
            (w/miss the-cache k value)
            (increment-upsert-statistic! this)
            (logr/debug "<" id :upsert k value)
            value)))))

  (lookup-or-miss! [this k]
    (locking write-lock
      (ensure-initialized! this)

      (if (w/has? the-cache k)
        (w/lookup the-cache k)

        (let [value (miss-fn k)]
          (if (= absent value)
            (logr/debug id :skip-update k)
            (do
              (logr/debug ">" id :insert k value)
              (ensure-initialized! this)
              (w/miss the-cache k value)
              (increment-upsert-statistic! this)
              (logr/debug "<" id :upsert k value)
              value))))))

  (evict! [this k]
    (locking write-lock
      (ensure-initialized! this)

      (when (w/has? the-cache k)
        (logr/debug ">" id :evict k)
        (ensure-initialized! this)
        (w/evict the-cache k)
        (increment-evict-statistic! this)
        (logr/debug "<" id :evict k)))))

(defn basic [{:keys [depends-on id miss-fn reload-fn]}]
  (assert (not (contains? @caches id)) (format "error overriding cache id %s" id))
  (let [reset-in-progress? false
        initialized? false
        statistics (if (:dev rems.config/env)
                     (select-keys initial-statistics [:reload :upsert :evict]) ; :get statistics can become big quickly
                     initial-statistics)
        read-lock (Object.)
        write-lock (Object.)
        the-cache (w/basic-cache-factory {})
        cache (->RefreshableCache id
                                  read-lock
                                  write-lock
                                  (atom statistics)
                                  (atom reset-in-progress?)
                                  (atom initialized?)
                                  the-cache
                                  miss-fn
                                  (or reload-fn (constantly {})))]
    (swap! caches assoc id cache)
    (swap! caches-dag dep/depend id depends-on) ; noop when empty depends-on
    (add-watch the-cache id reset-dependents-on-change!)
    cache))
