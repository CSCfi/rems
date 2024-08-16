(ns rems.service.caches
  (:require [rems.cache :as cache]
            [rems.db.workflow]))

(def db-caches
  "Caches that use existing database."
  #{#'rems.db.workflow/workflow-cache})

(defn start-caches! [& caches]
  (->> caches
       (mapcat identity)
       (run! #(cache/ensure-initialized! (cond-> % (var? %) var-get)))))

(defn reset-caches! [& caches]
  (->> caches
       (mapcat identity)
       (run! #(cache/reset! (cond-> % (var? %) var-get)))))

(defn start-all-caches! [] (start-caches! db-caches))
(defn reset-all-caches! [] (reset-caches! db-caches))
