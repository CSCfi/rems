(ns rems.cache
  (:require [clojure.core.cache.wrapped :as w]
            [clojure.tools.logging.readable :as logr]
            [rems.common.util :refer [build-index]]
            [rems.common.dependency :as dep]
            [rems.config]))

(def ^:private caches (atom nil))
(def ^:private caches-dag (atom (dep/make-graph)))

(def ^{:doc "Value that tells cache to skip entry."} absent ::absent)

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

(defn- reset-dependents-on-change! [id _cache _old-value _new-value]
  (when-some [dependents (seq (dep/get-all-dependents @caches-dag id))]
    (logr/debug ">" id :reset-dependents {:dependents dependents})
    (locking caches
      (doseq [dep-id dependents
              :let [dep-cache (get @caches dep-id)]]
        (set-uninitialized! dep-cache)))
    (logr/debug "<" id :reset-dependents)))

(defrecord RefreshableCache [id
                             ^clojure.lang.IAtom statistics
                             ^clojure.lang.IAtom initialized?
                             ^clojure.lang.IAtom the-cache
                             ^clojure.lang.IFn miss-fn
                             ^clojure.lang.IFn reload-fn]
  CacheStatisticsProtocol

  (export-statistics! [this]
    (let [value @statistics]
      (reset! statistics {:reload 0 :upsert 0 :evict 0})
      value))

  (increment-reload-statistic! [this] (some-> statistics (swap! update :reload inc)))
  (increment-upsert-statistic! [this] (some-> statistics (swap! update :upsert inc)))
  (increment-evict-statistic! [this] (some-> statistics (swap! update :evict inc)))

  RefreshableCacheProtocol

  (reload! [this]
    (locking caches
      (let [deps (get-cache-dependencies id)]
        (logr/debug ">" id :reload)

        (doseq [dep-cache deps]
          (ensure-initialized! dep-cache))

        (if (empty? deps)
          (w/seed the-cache (reload-fn))

          (let [dep-caches (build-index {:keys [:id] :value-fn entries!} deps)]
            (w/seed the-cache (reload-fn dep-caches))))

        (let [entries @the-cache]
          (increment-reload-statistic! this)
          (reset! initialized? true)
          (logr/debug "<" id :reload {:count (count entries)})
          @the-cache))))

  (ensure-initialized! [this]
    ;; NB: reading requires no locking
    (if @initialized?
      @the-cache
      (reload! this)))

  (set-uninitialized! [this]
    ;; NB: if we ever have initialized we have still entries
    ;; even after uninitializing, so we can read old data
    ;; NB: we must lock here so that we don't conflict
    ;; with reload!
    (locking caches
      (reset! initialized? false)))

  (entries! [this]
    ;; NB: reading requires no locking
    (ensure-initialized! this))

  (has? [this k]
    ;; NB: reading requires no locking
    (ensure-initialized! this)
    (w/has? the-cache k))

  (lookup! [this k]
    ;; NB: reading requires no locking
    (ensure-initialized! this)
    (w/lookup the-cache k))

  (lookup! [this k not-found]
    ;; NB: reading requires no locking
    (ensure-initialized! this)
    (w/lookup the-cache k not-found))

  (miss! [this k]
    (ensure-initialized! this)

    (locking caches
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
    (ensure-initialized! this)

    (if (w/has? the-cache k)
      (w/lookup the-cache k)

      (locking caches
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
    (ensure-initialized! this)

    (locking caches
      (when (w/has? the-cache k)
        (logr/debug ">" id :evict k)
        (ensure-initialized! this)
        (w/evict the-cache k)
        (increment-evict-statistic! this)
        (logr/debug "<" id :evict k)))))

(defn- ensure-cache-id-unique! [id]
  (when (contains? @caches id)
    ;; NB: even if config is not started, we default to assert
    (if (:dev rems.config/env)
      (logr/warnf "overriding cache id %s" id)
      (assert false (format "error overriding cache id %s" id)))))

(defn basic [{:keys [depends-on id miss-fn reload-fn]}]
  (or (ensure-cache-id-unique! id)
      (let [initialized? false
            statistics {}
            the-cache (w/basic-cache-factory {})
            cache (->RefreshableCache id
                                      (atom statistics)
                                      (atom initialized?)
                                      the-cache
                                      miss-fn
                                      (or reload-fn (constantly {})))
            _ (export-statistics! cache) ; NB: initializes statistics
            ]
        (swap! caches assoc id cache)
        (swap! caches-dag dep/depend id depends-on) ; noop when empty depends-on
        (add-watch the-cache id reset-dependents-on-change!)
        cache)))
