(ns rems.cache
  (:refer-clojure :exclude [reset!])
  (:require [clojure.core.cache :as c]
            [clojure.core.cache.wrapped :as w]
            [clojure.tools.logging.readable :as logr]
            [medley.core :refer [assoc-some]]
            [rems.util :refer [assert-ex]]))

(defprotocol RefreshableCacheProtocol
  "Protocol for cache wrapper that can refresh the underlying cache."

  (ensure-initialized! [this]
    "Reloads the cache if it is not ready.")
  (reset! [this]
    "Resets cache to uninitialized state. Next call to ensure-initialized will reload the cache.")
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
  (evict-and-miss! [this k] [this k value-fn]
    "Removes `k` from cache and immediately performs lookup to update the cache for `k` to `(miss-fn k)` or `(value-fn k)`."))

(defrecord RefreshableCache [id
                             ^clojure.lang.Volatile initialized?
                             ^clojure.lang.IAtom the-cache
                             ^clojure.lang.IFn wrap-miss-fn
                             ^clojure.lang.IFn on-evict
                             ^clojure.lang.IFn miss-fn
                             ^clojure.lang.IFn reload-fn
                             ^clojure.lang.IFn reset-fn]
  RefreshableCacheProtocol

  (ensure-initialized! [this]
    (when-not @initialized? ; no need to acquire lock if already initialized
      (locking id
        (when-not @initialized?
          (logr/debug :reload id)
          (w/seed the-cache (if reload-fn
                              (reload-fn)
                              {}))
          (logr/info :reloaded id {:count (count @the-cache)})
          (vreset! initialized? true))))
    the-cache)

  (reset! [this]
    (locking initialized?
      (logr/debug :reset id {:reset-fn reset-fn})
      (w/seed the-cache (if reset-fn
                          (reset-fn)
                          {}))
      (vreset! initialized? false)
      this))

  (has? [this k] (w/has? (ensure-initialized! this) k))

  (entries! [this] @(ensure-initialized! this))

  (lookup! [this k] (w/lookup (ensure-initialized! this) k))
  (lookup! [this k not-found] (w/lookup (ensure-initialized! this) k not-found))

  (lookup-or-miss! [this k] (lookup-or-miss! this k miss-fn))
  (lookup-or-miss! [this k value-fn]
    (if (has? this k)
      (w/lookup the-cache k)
      (locking id
        (w/lookup-or-miss the-cache k wrap-miss-fn value-fn))))

  (evict! [this k]
    (locking id
      (w/evict (ensure-initialized! this) k))
    (when on-evict
      (on-evict k))
    this)

  (evict-and-miss! [this k] (evict-and-miss! this k miss-fn))
  (evict-and-miss! [this k value-fn]
    (locking id
      (evict! this k)
      (w/lookup-or-miss the-cache k wrap-miss-fn value-fn))))

(defn- wrap-cache-miss-fn [id]
  (fn [f item]
    (assert-ex (fn? f) {:id id :error "cannot update cache entry, missing value function"})
    (logr/debug :miss id {:entry item})
    (f item)))

(defn- make-cache [cache-factory-fn {:keys [id miss-fn on-evict reload-fn reset-fn]} & [cache-opts]]
  (let [the-cache (if (seq cache-opts)
                    (cache-factory-fn {} cache-opts)
                    (cache-factory-fn {}))
        initialized? false]
    (->RefreshableCache id
                        (volatile! initialized?)
                        (atom the-cache)
                        (wrap-cache-miss-fn id)
                        on-evict
                        miss-fn
                        reload-fn
                        reset-fn)))



;;; public functions for creating new cache, by type

(defn basic
  "Wrapper for basic-cache-factory."
  [opts]
  (make-cache c/basic-cache-factory opts))

(defn fifo
  "Wrapper for fifo-cache-factory."
  [{:keys [threshold] :as opts}]
  (make-cache c/fifo-cache-factory opts (assoc-some {}
                                                    :threshold threshold)))

(defn lru
  "Wrapper for lru-cache-factory."
  [{:keys [threshold] :as opts}]
  (make-cache c/lru-cache-factory opts (assoc-some {}
                                                   :threshold threshold)))

(defn ttl
  "Wrapper for ttl-cache-factory."
  [{:keys [ttl] :as opts}]
  (make-cache c/ttl-cache-factory opts (assoc-some {}
                                                   :ttl ttl)))

(defn lu
  "Wrapper for lu-cache-factory."
  [{:keys [threshold] :as opts}]
  (make-cache c/lu-cache-factory opts (assoc-some {}
                                                  :threshold threshold)))

(defn lirs
  "Wrapper for lirs-cache-factory."
  [{:keys [s-history-limit q-history-limit] :as opts}]
  (make-cache c/lirs-cache-factory opts (assoc-some {}
                                                    :s-history-limit s-history-limit
                                                    :q-history-limit q-history-limit)))

(defn soft
  "Wrapper for soft-cache-factory."
  [opts]
  (make-cache c/soft-cache-factory opts))

(comment
  (def basic-cache
    (basic {:id :test
            :miss-fn (fn [k] {:k k})
            :reload-fn (fn []
                         (println "reloading basic cache")
                         {"x" {:k true} "y" {:k false}})})))
