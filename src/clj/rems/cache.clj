(ns rems.cache
  (:require [clojure.core.cache :as c]
            [clojure.core.cache.wrapped :as w]
            [clojure.tools.logging.readable :as logr]
            [medley.core :refer [update-existing]]
            [mount.core :as mount]
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

(mount/defstate dependency-loaders
  :start (do
           (logr/info "starting dependency-loaders thread pool")
           (concurrency/work-stealing-thread-pool))
  :stop (when dependency-loaders
          (logr/info "stopping dependency-loaders thread pool")
          (concurrency/stop! dependency-loaders {:timeout-ms 5000})))

(defn submit-tasks! [& fns]
  (concurrency/submit! dependency-loaders fns))

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
          (locking w-lock ; ensure cache state is unchanged while this thread reads (and potentially writes)
            (let [id (:id *cache*)
                  entries @(ensure-initialized! *cache*)]
              (swap! *cache-deps* assoc id entries))
            (.countDown *progress*) ; signal progress to parent thread
            (.await *finished*))))))) ; once all dependency joiners are finished

(defn- create-reset-dependent-task
  "Creates a low-level threaded function that sets `cache` into uninitialized state, triggering
   next cache access to reload. Synchronizes via `progress` and `finished` with other threads."
  [cache finished progress]
  (binding [*cache* cache
            *finished* finished
            *progress* progress]
    (bound-fn []
      (if (not @(:initialized? *cache*))
        (.countDown *progress*) ; bail out early
        (let [r-lock (:read-lock *cache*)]
          (locking r-lock
            (set-uninitialized! *cache*)
            (.countDown *progress*)
            (.await *finished*)))))))

(defn- reset-dependents-on-change! [id _cache _old-value _new-value]
  (when-let [dependents (seq (dep/get-all-dependents @caches-dag id))]
    (let [finished (CountDownLatch. 1)
          progress (CountDownLatch. (count dependents))]
      (logr/debug ">" id :reset-dependents {:dependents dependents})
      (submit-tasks! (for [dep-id dependents]
                       (create-reset-dependent-task (get @caches dep-id)
                                                    finished
                                                    progress)))
      (.await progress 3000 TimeUnit/MILLISECONDS)
      (.countDown finished)
      (logr/debug "<" id :reset-dependents))))

(defrecord RefreshableCache [id
                             read-lock
                             write-lock
                             ^clojure.lang.Volatile statistics
                             ^clojure.lang.IAtom initialized?
                             ^clojure.lang.IAtom the-cache
                             ^clojure.lang.IFn miss-fn
                             ^clojure.lang.IFn reload-fn]
  CacheStatisticsProtocol

  (export-statistics! [this]
    (let [stats @statistics]
      (vreset! statistics (select-keys initial-statistics (keys stats)))
      stats))

  (increment-get-statistic! [this] (vswap! statistics update-existing :get inc))
  (increment-reload-statistic! [this] (vswap! statistics update :reload inc))
  (increment-upsert-statistic! [this] (vswap! statistics update :upsert inc))
  (increment-evict-statistic! [this] (vswap! statistics update :evict inc))

  RefreshableCacheProtocol

  (reload! [this]
    (locking write-lock
      (logr/debug ">" id :reload)
      (let [deps (get-cache-dependencies id)]
        (cond
          (empty? deps)
          (w/seed the-cache (reload-fn))

          :else
          ;; XXX: dependency resolver can synchronize immediate dependencies, but
          ;; it does not properly synchronize transitive dependencies (so they might every now and then be off sync).
          ;; this might be workable by e.g. passing latches down.
          (let [finished (CountDownLatch. 1)
                progress  (CountDownLatch. (count deps))
                cache-deps (atom {})]
            (submit-tasks! (for [c deps]
                             (create-join-dependency-task c cache-deps finished progress)))
            (.await progress 3000 TimeUnit/MILLISECONDS)
            (w/seed the-cache (reload-fn @cache-deps))
            (.countDown finished))))
      (reset! initialized? true)
      (increment-reload-statistic! this)
      (logr/debug "<" id :reload {:count (count @the-cache)})))

  (ensure-initialized! [this]
    (when-not @initialized?
      (reload! this))
    (increment-get-statistic! this)
    the-cache)

  (set-uninitialized! [this]
    (reset! initialized? false))

  (entries! [this]
    (locking write-lock
      @(ensure-initialized! this)))

  (has? [this k]
    (c/has? (entries! this) k))

  (lookup! [this k]
    (c/lookup (entries! this) k))

  (lookup! [this k not-found]
    (c/lookup (entries! this) k not-found))

  (miss! [this k]
    (locking write-lock
      (let [value (miss-fn k)]
        (if (= absent value)
          (logr/debug id :skip-update k)
          (do
            (logr/debug ">" id :upsert k value)
            (w/miss (ensure-initialized! this) k value)
            (increment-upsert-statistic! this)
            (logr/debug "<" id :upsert k value)
            value)))))

  (lookup-or-miss! [this k]
    (locking write-lock
      (if (has? this k)
        (lookup! this k)
        (miss! this k))))

  (evict! [this k]
    (locking write-lock
      (when (has? this k)
        (logr/debug ">" id :evict k)
        (w/evict (ensure-initialized! this) k)
        (increment-evict-statistic! this)
        (logr/debug "<" id :evict k)))))

(defn basic [{:keys [depends-on id miss-fn reload-fn]}]
  (assert (not (contains? @caches id)) (format "error overriding cache id %s" id))
  (let [initialized? false
        statistics (if (:dev rems.config/env)
                     (select-keys initial-statistics [:reload :upsert :evict]) ; :get statistics can become big quickly
                     initial-statistics)
        read-lock (Object.)
        write-lock (Object.)
        the-cache (w/basic-cache-factory {})
        cache (->RefreshableCache id
                                  read-lock
                                  write-lock
                                  (volatile! statistics)
                                  (atom initialized?)
                                  the-cache
                                  miss-fn
                                  (or reload-fn (constantly {})))]
    (swap! caches assoc id cache)
    (swap! caches-dag dep/depend id depends-on) ; noop when empty depends-on
    (add-watch the-cache id reset-dependents-on-change!)
    cache))
