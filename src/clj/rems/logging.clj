(ns rems.logging
  (:require [clojure.test :refer [deftest is testing]])
  (:import (org.slf4j MDC)))

(defn with-mdc* [m f]
  (let [m (->> m
               (map (fn [[k v]]
                      [(if (keyword? k)
                         (name k)
                         k)
                       (str v)]))
               (into {}))]
    (try
      (doseq [[k v] m]
        (when (seq v)
          (MDC/put k v)))
      (f)
      (finally
        (doseq [k (keys m)]
          (MDC/remove k))))))

(defmacro with-mdc
  "Adds a map of values to SLF4J's MDC for logging context."
  [m & body]
  `(with-mdc* ~m (fn [] ~@body)))

(deftest test-with-mdc
  (testing "adds keys to context"
    (with-mdc {"foo" "bar"}
      (is (= "bar" (MDC/get "foo")))))
  (testing "removes keys from context afterwards"
    (with-mdc {"foo" "bar"})
    (is (nil? (MDC/get "foo"))))
  (testing "can add multiple keys at once"
    (with-mdc {"foo" "one"
               "bar" "two"}
      (is (= "one" (MDC/get "foo")))
      (is (= "two" (MDC/get "bar"))))
    (is (empty? (MDC/getCopyOfContextMap))))
  (testing "converts keyword keys to string"
    (with-mdc {:foo "bar"}
      (is (= "bar" (MDC/get "foo")))))
  (testing "converts non-string values to string"
    (with-mdc {"foo" :bar}
      (is (= ":bar" (MDC/get "foo"))))
    (with-mdc {"foo" 123}
      (is (= "123" (MDC/get "foo"))))
    (with-mdc {"foo" true}
      (is (= "true" (MDC/get "foo")))))
  (testing "ignores nil values"
    (with-mdc {"foo" nil}
      (is (empty? (MDC/getCopyOfContextMap)))))
  (testing "ignores empty string values"
    (with-mdc {"foo" ""}
      (is (empty? (MDC/getCopyOfContextMap)))))
  (testing "does not ignore false values"
    (with-mdc {"foo" false}
      (is (= "false" (MDC/get "foo"))))))
