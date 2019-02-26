(ns rems.logging
  "Logback's Mapped Diagnostic Context (MDC) is a thread-local place
   for storing information about the current request. The information
   can then be included in the logs via Logback's appender patterns.
   See https://logback.qos.ch/manual/mdc.html for more information."
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
  "Adds a map of values to Logback's MDC for use within `body`."
  [m & body]
  `(with-mdc* ~m (fn [] ~@body)))

(deftest test-with-mdc
  (testing "adds keys to context"
    (with-mdc {"foo" "bar"}
      (is (= "bar" (MDC/get "foo")))
      (is (= {"foo" "bar"} (MDC/getCopyOfContextMap)))))

  (testing "removes keys from context afterwards"
    (with-mdc {"foo" "bar"})
    (is (empty? (MDC/getCopyOfContextMap)) "key is removed after clean exit")

    (is (thrown? RuntimeException
                 (with-mdc {"foo" "bar"}
                   (throw (RuntimeException. "dummy")))))
    (is (empty? (MDC/getCopyOfContextMap)) "key is removed after exception"))

  (testing "returns the value which is returned from the sexp body"
    (is (= "result" (with-mdc {"foo" "bar"}
                      "result"))))

  (testing "can add multiple keys at once"
    (with-mdc {"foo" "one"
               "bar" "two"}
      (is (= {"foo" "one"
              "bar" "two"} (MDC/getCopyOfContextMap)) "inside sexp"))
    (is (empty? (MDC/getCopyOfContextMap)) "after sexp"))

  (testing "contexts can be nested"
    (with-mdc {"foo" "one"}
      (with-mdc {"bar" "two"}
        (is (= {"foo" "one"
                "bar" "two"} (MDC/getCopyOfContextMap)) "inside nested sexp"))
      (is (= {"foo" "one"} (MDC/getCopyOfContextMap)) "after nested sexp")))

  (testing "converts keyword keys to string"
    (with-mdc {:foo "bar"}
      (is (= {"foo" "bar"} (MDC/getCopyOfContextMap)))))

  (testing "converts non-string values to string"
    (with-mdc {"foo" :bar}
      (is (= ":bar" (MDC/get "foo"))))
    (with-mdc {"foo" 123}
      (is (= "123" (MDC/get "foo"))))
    (with-mdc {"foo" true}
      (is (= "true" (MDC/get "foo")))))

  (testing "ignores nil values"
    (with-mdc {"foo" nil}
      (is (nil? (MDC/get "foo")) "value is nil")
      (is (empty? (MDC/getCopyOfContextMap)) "key is not even added to context")))

  (testing "ignores empty string values"
    (with-mdc {"foo" ""}
      (is (nil? (MDC/get "foo")) "value is nil")
      (is (empty? (MDC/getCopyOfContextMap)) "key is not even added to context")))

  (testing "does not ignore false values"
    (with-mdc {"foo" false}
      (is (= "false" (MDC/get "foo"))))))
