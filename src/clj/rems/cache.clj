(ns rems.cache
  (:refer-clojure :exclude [reset!])
  (:require [clojure.core.cache :as c]
            [clojure.core.cache.wrapped :as w]
            [clojure.tools.logging.readable :as logr]
            [medley.core :refer [update-existing]]
            [rems.common.dependency :as dep]
            [rems.config]))

(defprotocol CacheStatisticsProtocol
  "Run-time cache statistics."

  (export-statistics! [this]
    "Retrieves runtime statistics from cache, and resets appropriate counters.")
  (increment-get-statistic! [this]
    "Increments cache access counter.")
  (increment-reload-statistic! [this]
    "Increments cache reload counter.")
  (increment-reset-statistic! [this]
    "Increments cache reset counter.")
  (increment-upsert-statistic! [this]
    "Increments cache insert/update counter.")
  (increment-evict-statistic! [this]
    "Increments cache evict counter."))

(defprotocol RefreshableCacheProtocol
  "Protocol for cache wrapper that can refresh the underlying cache."

  (ensure-initialized! [this]
    "Reloads the cache if it is not ready.")
  (reset! [this]
    "Resets the cache to uninitialized state. Next call to ensure-initialized will reload the cache.")
  (has? [this k]
    "Checks if cache contains `k`.")
  (entries! [this]
    "Retrieves the current cache snapshot.")
  (lookup! [this k] [this k not-found]
    "Retrieves `k` from cache if it exists, or `not-found` if specified.")
  (lookup-or-miss! [this k] [this k value-fn]
    "Retrieves `k` from cache if it exists, else updates the cache for `k` to `(miss-fn k)` or `(value-fn k)` and performs the lookup again.")
  (evict! [this k]
    "Removes `k` from cache.")
  (miss! [this k]
    "Updates the cache for `k` to `(miss-fn k)` and returns the updated value."))

(def ^:private caches (atom nil))
(def ^:private caches-dag (atom (dep/make-graph)))

(defn get-all-caches [] (some-> @caches vals))

(defn get-cache-dependents [id]
  (->> (dep/get-dependents @caches-dag id)
       (map #(get @caches %))))

(defn get-cache-dependencies [id]
  (->> (dep/get-dependencies @caches-dag id)
       (map #(get @caches %))))

(defn- reset-dependent-caches! [id]
  (when-let [dependents (seq (get-cache-dependents id))]
    (logr/debug :reset-dependents id {:dependents (mapv :id dependents)})
    (run! rems.cache/reset! dependents)))

(def ^{:doc "Value that tells cache to skip entry."} absent ::absent)

(def ^:private initial-statistics {:get 0 :reload 0 :reset 0 :upsert 0 :evict 0})

(defrecord RefreshableCache [id
                             ^clojure.lang.Volatile statistics
                             ^clojure.lang.Volatile initialized?
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
  (increment-reset-statistic! [this] (vswap! statistics update :reset inc))
  (increment-upsert-statistic! [this] (vswap! statistics update :upsert inc))
  (increment-evict-statistic! [this] (vswap! statistics update :evict inc))

  RefreshableCacheProtocol

  (ensure-initialized! [this]
    (increment-get-statistic! this)
    (when-not @initialized? ; no need to acquire lock if already initialized
      (logr/debug :reload id)
      (locking id
        (when-not @initialized?
          (increment-reload-statistic! this)
          (if-let [deps (->> (get-cache-dependencies id)
                             (into {} (map (juxt :id entries!)))
                             not-empty)]
            (w/seed the-cache (reload-fn deps))
            (w/seed the-cache (reload-fn)))
          (vreset! initialized? true)
          (reset-dependent-caches! id)
          (logr/info :reload-finish id {:count (count @the-cache)}))))
    this)

  (reset! [this]
    (when @initialized?
      (logr/debug :reset id)
      (locking id
        (when @initialized?
          (increment-reset-statistic! this)
          (w/seed the-cache {})
          (vreset! initialized? false)
          (reset-dependent-caches! id)
          (logr/debug :reset-finish id))))
    this)

  (has? [this k]
    (ensure-initialized! this)
    (w/has? the-cache k))

  (entries! [this]
    (ensure-initialized! this)
    @the-cache)

  (lookup! [this k]
    (ensure-initialized! this)
    (w/lookup the-cache k))

  (lookup! [this k not-found]
    (ensure-initialized! this)
    (w/lookup the-cache k not-found))

  ;; supports use case where cache does not have pre-determined reload function
  (lookup-or-miss! [this k] (lookup-or-miss! this k miss-fn))
  (lookup-or-miss! [this k value-fn]
    (ensure-initialized! this)
    (if (w/has? the-cache k)
      (w/lookup the-cache k)

      (let [value (value-fn k)
            skip-update? (= ::absent value)]

        (when-not skip-update?
          (increment-upsert-statistic! this)
          (locking id
            (w/miss the-cache k value)
            (reset-dependent-caches! id))
          value))))

  (evict! [this k]
    (ensure-initialized! this)
    (increment-evict-statistic! this)
    (locking id
      (w/evict the-cache k)
      (reset-dependent-caches! id)))

  (miss! [this k]
    (ensure-initialized! this)
    (let [value (miss-fn k)
          skip-update? (= ::absent value)]

      (when-not skip-update?
        (increment-upsert-statistic! this)
        (locking id
          (w/miss the-cache k value)
          (reset-dependent-caches! id))
        value))))

(defn- make-cache! [cache-factory-fn {:keys [base depends-on id miss-fn reload-fn]} & [cache-opts]]
  (let [initialized? false
        statistics (if (:dev rems.config/env)
                     (select-keys initial-statistics [:reload :reset :upsert :evict]) ; get statistics can become big quickly
                     initial-statistics)
        the-cache (if cache-opts
                    (cache-factory-fn (or base {}) cache-opts)
                    (cache-factory-fn (or base {})))
        cache (->RefreshableCache id
                                  (volatile! statistics)
                                  (volatile! initialized?)
                                  (atom the-cache)
                                  miss-fn
                                  (or reload-fn (constantly {})))]
    (swap! caches assoc id cache)
    (swap! caches-dag dep/depend id depends-on) ; noop when empty depends-on
    cache))

(defn basic
  {:arglists '([{:keys [base depends-on id miss-fn reload-fn]}])}
  [opts]
  (make-cache! c/basic-cache-factory opts))
