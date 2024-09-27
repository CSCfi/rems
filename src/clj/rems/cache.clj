(ns rems.cache
  (:refer-clojure :exclude [reset!])
  (:require [clojure.core.cache :as c]
            [clojure.core.cache.wrapped :as w]
            [clojure.tools.logging.readable :as logr]
            [medley.core :refer [update-existing]]
            [rems.common.dependency :as dep]
            [rems.config]))

(def ^:private caches (atom nil))
(def ^:private caches-dag (atom (dep/make-graph)))

(def ^{:doc "Value that tells cache to skip entry."} absent ::absent)

(def ^:private initial-statistics {:get 0 :reload 0 :reset 0 :upsert 0 :evict 0})

(defprotocol RefreshableCacheProtocol
  "Protocol for cache wrapper that can refresh the underlying cache."

  (reload! [this]
    "Reloads the cache to `(reload-fn)`, or `(reload-fn deps)` if `depends-on` was specified.")
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
  (increment-reset-statistic! [this]
    "Increments cache reset counter.")
  (increment-upsert-statistic! [this]
    "Increments cache insert/update counter.")
  (increment-evict-statistic! [this]
    "Increments cache evict counter."))

(defn get-all-caches [] (some-> @caches vals))

(defn get-cache-dependencies [id]
  (->> (dep/get-dependencies @caches-dag id)
       (map #(get @caches %))))

(defrecord RefreshableCache [id
                             write-lock
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

  (reload! [this]
    (locking write-lock
      (logr/debug ">" id :reload)
      (if-let [deps (seq (get-cache-dependencies id))]
        (w/seed the-cache (reload-fn (into {} (map (juxt :id entries!)) deps)))
        (w/seed the-cache (reload-fn)))
      (vreset! initialized? true)
      (increment-reload-statistic! this)
      (logr/debug "<" id :reload {:count (count @the-cache)})))

  (ensure-initialized! [this]
    (when-not @initialized?
      (locking id
        (when-not @initialized?
          (reload! this))))
    (increment-get-statistic! this)
    the-cache)

  (reset! [this]
    (when @initialized?
      (locking id
        (when @initialized?
          (locking write-lock
            (logr/debug ">" id :reset)
            (vreset! initialized? false)
            (w/seed the-cache {})
            (increment-reset-statistic! this)
            (logr/debug "<" id :reset)))))
    this)

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

(defn- reset-dependent-caches-on-change! [id _cache _old-value _new-value]
  (when-let [dependents (seq (dep/get-all-dependents @caches-dag id))]
    (logr/debug ">" id :reset-dependents {:dependents dependents})
    (run! #(rems.cache/reset! (get @caches %)) dependents)
    (logr/debug "<" id :reset-dependents)))

(defn basic [{:keys [depends-on id miss-fn reload-fn]}]
  (let [initialized? false
        statistics (if (:dev rems.config/env)
                     (select-keys initial-statistics [:reload :reset :upsert :evict]) ; get statistics can become big quickly
                     initial-statistics)
        write-lock (Object.)
        the-cache (w/basic-cache-factory {})
        cache (->RefreshableCache id
                                  write-lock
                                  (volatile! statistics)
                                  (volatile! initialized?)
                                  the-cache
                                  miss-fn
                                  (or reload-fn (constantly {})))]
    (assert (not (contains? @caches id)) (format "error overriding cache id %s" id))
    (swap! caches assoc id cache)
    (add-watch the-cache id reset-dependent-caches-on-change!)
    (swap! caches-dag dep/depend id depends-on) ; noop when empty depends-on
    cache))
