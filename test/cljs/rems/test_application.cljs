(ns rems.test-application
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [hiccup-find.core :refer [hiccup-find]]
            [re-frame.core :as rf]
            [rems.application :refer [basic-field text-field texta-field toggle-diff-button decode-option-keys encode-option-keys normalize-option-key]]
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

(deftest basic-field-test
  ;; TODO: experiment of writing tests for view components; is there better tooling for testing components?
  (testing "link to show diff"
    (testing "no previous value"
      (is (not (contains-hiccup? toggle-diff-button (basic-field {:value "foo"} "<editor-component>")))))
    (testing "has previous value"
      (is (contains-hiccup? toggle-diff-button (basic-field {:value "foo", :previous-value "bar"} "<editor-component>"))))
    (testing "previous value is same as current value"
      (is (not (contains-hiccup? toggle-diff-button (basic-field {:value "foo", :previous-value "foo"} "<editor-component>")))))))

(deftest maxlength-field-test
  (is (not (empty? (hiccup-find [:input {:max-length 10}]
                                (text-field {:id "id"
                                             :inputprompt "placeholder"
                                             :value "hello"
                                             :maxlength 10})))))
  (is (not (empty? (hiccup-find [textarea {:max-length 10}]
                                (texta-field {:id "id"
                                              :inputprompt "placeholder"
                                              :value "hello"
                                              :maxlength 10}))))))

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
