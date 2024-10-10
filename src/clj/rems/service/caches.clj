(ns rems.service.caches
  (:require [clojure.tools.logging.readable :as logr]
            [mount.core :as mount]
            [rems.cache :as cache]))

(defn get-all-caches []
  (mount/start #'rems.cache/dependency-loaders)
  (cache/get-all-caches))

(defn start-all-caches! []
  (logr/info :start-all)
  (mount/start #'rems.cache/dependency-loaders)
  (run! cache/ensure-initialized! (get-all-caches)))

(defn reset-all-caches! []
  (logr/info :reset-all)
  (mount/start #'rems.cache/dependency-loaders)
  (run! cache/set-uninitialized! (get-all-caches)))

(defn export-all-cache-statistics! []
  (logr/info :statistics-all)
  (mount/start #'rems.cache/dependency-loaders)
  (into {} (map (juxt :id cache/export-statistics!)) (get-all-caches)))
