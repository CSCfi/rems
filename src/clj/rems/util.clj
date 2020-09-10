(ns rems.util
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [buddy.core.nonce :as buddy-nonce]
            [buddy.core.codecs :as buddy-codecs]
            [rems.context :as context])
  (:import [clojure.lang Atom]
           [java.io ByteArrayOutputStream FileInputStream]))

(defn errorf
  "Throw a RuntimeException, args passed to `clojure.core/format`."
  [& fmt-args]
  (throw (RuntimeException. (apply format fmt-args))))

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
   (:eppn user)))

(defn getx-user-id
  ([]
   (getx-user-id context/*user*))
  ([user]
   (getx user :eppn)))

(defn update-present
  "Like clojure.core/update, but does nothing if the key `k` does not exist in `m`."
  [m k f & args]
  (if (find m k)
    (apply update m k f args)
    m))

(deftest test-update-present
  (is (= {:a 1} (update-present {:a 1} :b (constantly true))))
  (is (= {:a 1 :b true} (update-present {:a 1 :b 2} :b (constantly true))))
  (is (= {:a 1 :b true} (update-present {:a 1 :b nil} :b (constantly true)))))

(defn secure-token
  []
  (let [randomdata (buddy-nonce/random-bytes 16)]
    (buddy-codecs/bytes->hex randomdata)))

(defn atom? [x]
  (instance? Atom x))

(defmacro assert-ex
  "Like assert but throw the result with ex-info and not as string. "
  ([x message]
   `(when-not ~x
      (throw (ex-info (str "Assert failed: " ~message "\n" (pr-str '~x))
                      (merge ~message {:expression '~x}))))))

(defmacro try-catch-ex
  "Wraps the code in `try` and `catch` and automatically unwraps the possible exception `ex-data` into regular result."
  [& body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (ex-data e#))))

(defn file-to-bytes
  "Returns contents of file (String or File) as byte array."
  [file]
  (with-open [input (FileInputStream. (io/file file))
              buffer (ByteArrayOutputStream.)]
    (io/copy input buffer)
    (.toByteArray buffer)))
