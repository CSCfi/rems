(ns rems.test-fields
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [re-frame.core :as rf]
            [rems.fields :refer [field-wrapper toggle-diff-button decode-option-keys encode-option-keys normalize-option-key]]
            [rems.atoms :refer [textarea]]
            [rems.spa]
            [rems.text :refer [text]]))

(use-fixtures
 :once
 (fn [f]
   ;; TODO: load translations file
   (rf/dispatch [:initialize-db])
   (rf/dispatch [:loaded-translations {}])
   (f)))

(defn contains-hiccup? [needle haystack]
  (some #(= % needle) (tree-seq vector? identity haystack)))

(deftest field-wrapper-test
  ;; TODO: experiment of writing tests for view components; is there better tooling for testing components?
  (testing "link to show diff"
    (testing "no previous value"
      (is (not (contains-hiccup? toggle-diff-button (field-wrapper {:field/value "foo"} "<editor-component>")))))
    (testing "has previous value"
      (is (contains-hiccup? toggle-diff-button (field-wrapper {:field/value "foo", :field/previous-value "bar"} "<editor-component>"))))
    (testing "previous value is same as current value"
      (is (not (contains-hiccup? toggle-diff-button (field-wrapper {:field/value "foo", :field/previous-value "foo"} "<editor-component>")))))))

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
