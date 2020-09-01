(ns rems.test-fields
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            ;; [npm.axe-core :as axe]
            [re-frame.core :as rf]
            [rems.fields :refer [field-wrapper toggle-diff-button]]
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
      (is (not (contains-hiccup? toggle-diff-button (field-wrapper {:form/id "foo" :field/id "foo" :field/value "foo"} "<editor-component>")))))
    (testing "has previous value"
      (is (contains-hiccup? toggle-diff-button (field-wrapper {:form/id "foo" :field/id "foo" :field/value "foo", :field/previous-value "bar"} "<editor-component>"))))
    (testing "previous value is same as current value"
      (is (not (contains-hiccup? toggle-diff-button (field-wrapper {:form/id "foo" :field/id "foo" :field/value "foo" :field/previous-value "foo"} "<editor-component>")))))))
