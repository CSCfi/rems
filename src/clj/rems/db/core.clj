(ns rems.db.core
  {:ns-tracker/resource-deps ["sql/queries.sql"]}
  (:require [cheshire.generate :as cheshire]
            [clj-time.core :as time]
            [clj-time.jdbc] ;; convert db timestamps to joda-time objects
            [clojure.set :refer [superset?]]
            [cognitect.transit :as transit]
            [conman.core :as conman]
            [mount.core :refer [defstate]]
            [rems.config :refer [env]])
  (:import [org.joda.time DateTime ReadableInstant]))

(def joda-time-writer
  (transit/write-handler
   "m"
   (fn [v] (-> ^ReadableInstant v .getMillis))
   (fn [v] (-> ^ReadableInstant v .getMillis .toString))))

(cheshire/add-encoder
 DateTime
 (fn [c jsonGenerator]
   (.writeString jsonGenerator (-> ^ReadableInstant c .getMillis .toString))))

(defstate ^:dynamic *db*
          :start (cond
                   (:database-url env) (conman/connect! {:jdbc-url (:database-url env)})
                   (:database-jndi-name env) {:name (:database-jndi-name env)}
                   :else (throw (IllegalArgumentException. ":database-url or :database-jndi-name must be configured")))
          :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")

(defn assert-test-database! []
  (assert (= {:current_database "rems_test"}
             (get-database-name))))

(defn contains-all-kv-pairs? [supermap map]
  (superset? (set supermap) (set map)))

(defn apply-filters [filters coll]
  (let [filters (or filters {})]
    (filter #(contains-all-kv-pairs? % filters) coll)))

(defn now-active?
  ([start end]
   (now-active? (time/now) start end))
  ([now start end]
   (and (or (nil? start)
            (time/after? now start))
        (or (nil? end)
            (time/before? now end)))))

(defn assoc-active
  "Calculates and assocs :active? attribute based on current time and :start and :endt attributes.

   Current time can be passed in optionally."
  ([x]
   (assoc-active (time/now) x))
  ([now x]
   (assoc x :active? (now-active? now (:start x) (:endt x)))))
