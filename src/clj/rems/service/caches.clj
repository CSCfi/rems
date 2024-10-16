(ns rems.service.caches
  (:require [clojure.tools.logging.readable :as logr]
            [rems.cache :as cache]))

(defn get-all-caches []
  (cache/get-all-caches))

(defn start-all-caches! []
  (logr/info :start-all)
  (run! cache/ensure-initialized! (get-all-caches)))

(defn reset-all-caches! []
  (logr/info :reset-all)
  (run! cache/set-uninitialized! (get-all-caches)))

(defn export-all-cache-statistics! []
  (logr/info :statistics-all)
  (into {} (map (juxt :id cache/export-statistics!)) (get-all-caches)))
