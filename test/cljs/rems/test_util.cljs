(ns rems.test-util
  (:require [cljs.test :refer-macros [deftest is testing]]
            [cljs-time.core :as time]
            [cljs-time.format :as format]
            [rems.text :refer [time-format localize-time]]
            [rems.util :refer [decode-option-keys encode-option-keys normalize-option-key linkify remove-empty-keys]]))

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

(deftest option-keys-test
  (testing "whitespace is not allowed in a key"
    (is (= "foo" (normalize-option-key " f o o "))))
  (testing "encoding"
    (is (= "" (encode-option-keys #{})))
    (is (= "foo" (encode-option-keys #{"foo"})))
    (is (= "bar foo" (encode-option-keys #{"foo" "bar"})))
    (is (= "bar foo" (encode-option-keys #{"bar" "foo"}))))
  (testing "decoding"
    (is (= #{} (decode-option-keys "")))
    (is (= #{"foo"} (decode-option-keys "foo")))
    (is (= #{"foo" "bar"} (decode-option-keys "foo bar")))
    (is (= #{"foo" "bar"} (decode-option-keys "  foo  bar  "))))
  (testing "round-trip"
    (is (= #{} (decode-option-keys (encode-option-keys #{}))))
    (is (= #{"foo"} (decode-option-keys (encode-option-keys #{"foo"}))))
    (is (= #{"foo" "bar"} (decode-option-keys (encode-option-keys #{"foo" "bar"}))))))

(deftest test-linkify
  (let [link [:a {:target :_blank :href "http://www.abc.com"} "http://www.abc.com"]]
    (testing "retain original string"
      (is (= (apply str (linkify "a b c") "a b c"))))
    (testing "change link strings to hiccup links"
      (is (= (linkify "See http://www.abc.com")
             ["See " link]))
      (is (= (linkify "See https://www.abc.com")
             ["See " [:a {:target :_blank :href "https://www.abc.com"} "https://www.abc.com"]])))
    (testing "do not include subsequent punctuation marks in the link"
      (is (= (linkify "See http://www.abc.com.")
             ["See " link "."]))
      (is (= (linkify "See http://www.abc.com, please.")
             ["See " link ", please."]))
      (is (= (linkify "See http://www.abc.com?")
             ["See " link "?"]))
      (is (= (linkify "See http://www.abc.com...")
             ["See " link "..."]))
      (is (= (linkify "See http://www.abc.com!")
             ["See " link "!"])))
    (testing "do not include subsequent parentheses in the link"
      (is (= (linkify "(See http://www.abc.com.)")
             ["(See " link ".)"]))
      (is (= (linkify "(See http://www.abc.com?)")
             ["(See " link "?)"])))
    (testing "a link without http-prefix"
      (is (= (linkify "(See www-page at www.abc.com.)")
             ["(See www-page at "
              [:a {:target :_blank :href "http://www.abc.com"} "www.abc.com"]
              ".)"])))))

(deftest test-remove-empty-keys
  (is (= (remove-empty-keys {}) {}))
  (is (= (remove-empty-keys {:a :b}) {:a :b}))
  (is (= (remove-empty-keys {:a nil}) {}))
  (is (= (remove-empty-keys {:a {:b {:c nil} :d {:e :f}}}) {:a {:d {:e :f}}})))
