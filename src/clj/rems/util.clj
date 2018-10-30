(ns rems.util
  (:require [rems.context :as context]
            [rems.config :refer [env]]))

(defn errorf
  "Throw a RuntimeException, args passed to `clojure.core/format`."
  [& fmt-args]
  (throw (RuntimeException. (apply format fmt-args))))

(defn get-theme-attribute
  "Fetch the attribute value from the current theme."
  [attr-name]
  (get (:theme env) attr-name))

(defn getx
  "Like `get` but throws an exception if the key is not found."
  [m k]
  (let [e (get m k ::sentinel)]
    (if-not (= e ::sentinel)
      e
      (throw (ex-info "Missing required key" {:map m :key k})))))

(defn getx-in
  "Like `get-in` but throws an exception if the key is not found."
  [m ks]
  (reduce getx m ks))

(def never-match-route
  (constantly nil))

(defn get-user-id
  ([]
   (get-user-id context/*user*))
  ([user]
   (get user "eppn")))

(defn getx-user-id
  ([]
   (getx-user-id context/*user*))
  ([user]
   (getx user "eppn")))

(defn get-username
  ([]
   (get-username context/*user*))
  ([user]
   (get user "commonName")))

(defn get-user-mail
  ([]
   (get-user-mail context/*user*))
  ([user]
   (get user "mail")))
