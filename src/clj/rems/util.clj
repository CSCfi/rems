(ns rems.util
  (:require [rems.context :as context]))

(defn index-by
  "Index the collection coll with given keys ks.

  Result is a map indexed by the first key
  that contains a map indexed by the second key."
  [ks coll]
  (if (empty? ks)
    (first coll)
    (->> coll
         (group-by (first ks))
         (map (fn [[k v]] [k (index-by (rest ks) v)]))
         (into {}))))

(defn errorf
  "Throw a RuntimeException, args passed to clojure.core/format."
  [& fmt-args]
  (throw (RuntimeException. (apply format fmt-args))))

(defn get-user-id []
  (get context/*user* "eppn"))

(defn get-username
  ([]
   (get context/*user* "commonName"))
  ([user]
   (binding [context/*user* user]
     (get-username))))

(defn get-theme-attribute
  "Tries to first fetch a value from the current theme and falls back to default theme if a value is missing."
  [attr-name]
  (if-let [value (get context/*theme* attr-name)]
    value
    (get (context/load-theme "default")
         attr-name)))
