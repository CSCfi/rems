(ns rems.test.application
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [hiccup-find.core :refer [hiccup-find]]
            [re-frame.core :as rf]
            [rems.application :refer [basic-field text-field texta-field toggle-diff-button]]
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
  (is (not (empty? (hiccup-find [:input {:maxlength 10}]
                                (text-field {:id "id"
                                             :inputprompt "placeholder"
                                             :value "hello"
                                             :maxlength 10})))))
  (is (not (empty? (hiccup-find [textarea {:maxlength 10}]
                                (texta-field {:id "id"
                                             :inputprompt "placeholder"
                                             :value "hello"
                                             :maxlength 10}))))))
