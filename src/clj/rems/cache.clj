(ns rems.cache
  (:require [clojure.core.cache.wrapped :as cache]))


;; COMMON:
;; - rems.db
;;   - fetches from postgres
;;   - transforms between db and internal representation
;;     - plain Clojure data
;;     - Schema supported
;; - rems.service
;;   - implements main business logic
;;   - full internal representation of objects
;;   - joins related business entities from multiple rems.db namespaces
;; - rems.api
;;   - services exposed through REST API
;;   - transforms between HTTP API and internal representation
;;     - Schema in use
;;     - response codes
;;     - user info from headers
;;     - access rights
;; - rems.cache (NEW)
;;   - collection of caching functionality
;;   - centralized access to cache status, invalidation and statistics
;; - rems.custom (NEW, OPTIONAL)
;;   - access rights wrt. visibility of information
;;   - responsible for showing only the relevant items based on business logic
;;
;; OPTION 1
;; rems.api -> rems.service -> rems.cache -> rems.db -> pg
;; - cache mostly DB rows
;; - items are customized later in code so everything cached can be shared
;; - service joins related information at runtime
;;
;; OPTION 2
;; rems.api -> rems.cache -> rems.service -> rems.db -> pg
;; - cache the most complete representation
;; - cached items are customized per user so not everything cached can be shared
;;
;; OPTION 3
;; rems.api -> rems.custom -> rems.cache -> rems.service -> rems.db -> pg
;; - cache complete joined representation
;; - items are customized later in code so everything cached can be shared
;;
;; TRICKY: avoid cyclical dependencies

(def ^:private form-template-cache (cache/ttl-cache-factory {}))
(def ^:private catalogue-item-cache (cache/ttl-cache-factory {}))
(def ^:private license-cache (cache/ttl-cache-factory {}))
(def ^:private user-cache (cache/ttl-cache-factory {}))
(def ^:private users-with-role-cache (cache/ttl-cache-factory {}))
(def ^:private workflow-cache (cache/ttl-cache-factory {}))
(def ^:private blacklist-cache (cache/ttl-cache-factory {}))

(defn empty-injections-cache! [] ; TODO get rid of
  (swap! form-template-cache empty)
  (swap! catalogue-item-cache empty)
  (swap! license-cache empty)
  (swap! user-cache empty)
  (swap! users-with-role-cache empty)
  (swap! workflow-cache empty)
  (swap! blacklist-cache empty))

(defn empty-caches! []
  (empty-injections-cache!))

;; WHY?
;; in rems.db.applications
;; cached-injections (map-vals memoize fetcher-injections)

(defn cached [fv]
  (let [name (-> fv meta :name str)]
    (fn [& args]
      (prn :cached name args)
      (apply fv args))))

