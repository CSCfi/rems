(ns rems.test.util
  (:require [cljs.test :refer-macros [deftest is testing]]
            [cljs-time.core :as time]
            [cljs-time.format :as format]
            [rems.text :refer [time-format localize-time]]))

(def test-time #inst "1980-01-02T13:45:00.000Z")

(defn expected-time
  "Return a given time as a local DateTime instance formatted by rems.text/time-format.
   Note that exact time checking would make the tests break when run from a different timezone."
  [time]
  (format/unparse-local time-format (time/to-default-time-zone time)))

(deftest localize-time-test
  (is (= (expected-time test-time) (localize-time "1980-01-02T13:45:00.000Z")))
  (is (= nil (localize-time "")))
  (is (= (expected-time test-time) (localize-time #inst "1980-01-02T13:45:00.000Z")))
  (is (= nil (localize-time nil))))
