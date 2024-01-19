(ns rems.test-util
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [cljs-time.core :as time]
            [cljs-time.format :as format]
            [rems.text :refer [time-format localize-time]]
            [rems.testing :refer [init-spa-fixture]]
            [rems.util :refer [linkify]]))

(use-fixtures :each init-spa-fixture)

(def test-time #inst "1980-01-02T13:45:00.000Z")

(defn expected-time
  "Return a given time as a local DateTime instance formatted by rems.text/time-format.
   Note that exact time checking would make the tests break when run from a different timezone."
  [time]
  (format/unparse-local (time-format) (time/to-default-time-zone time)))

(deftest localize-time-test
  (is (= (expected-time test-time) (localize-time "1980-01-02T13:45:00.000Z")))
  (is (= nil (localize-time "")))
  (is (= (expected-time test-time) (localize-time #inst "1980-01-02T13:45:00.000Z")))
  (is (= nil (localize-time nil))))

(deftest test-linkify
  (let [link [:a {:target :_blank :href "http://www.abc.com"} "http://www.abc.com"]]
    (testing "retain original string"
      (is (= ["a b c"]
             (linkify "a b c"))))
    (testing "change link strings to hiccup links"
      (is (= ["See " link]
             (linkify "See http://www.abc.com")))
      (is (= ["See " [:a {:target :_blank :href "https://www.abc.com"} "https://www.abc.com"]]
             (linkify "See https://www.abc.com"))))
    (testing "do not include subsequent punctuation marks in the link"
      (is (= ["See " link "."]
             (linkify "See http://www.abc.com.")))
      (is (= ["See " link ", please."]
             (linkify "See http://www.abc.com, please.")))
      (is (= ["See " link "?"]
             (linkify "See http://www.abc.com?")))
      (is (= ["See " link "..."]
             (linkify "See http://www.abc.com...")))
      (is (= ["See " link "!"]
             (linkify "See http://www.abc.com!"))))
    (testing "do not include subsequent parentheses in the link"
      (is (= ["(See " link ".)"]
             (linkify "(See http://www.abc.com.)")))
      (is (= ["(See " link "?)"]
             (linkify "(See http://www.abc.com?)"))))
    (testing "a link without http-prefix"
      (is (= ["(See www-page at " [:a {:target :_blank :href "http://www.abc.com"} "www.abc.com"] ".)"]
             (linkify "(See www-page at www.abc.com.)"))))))
