(ns rems.test.application
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [re-frame.core :as rf]
            [rems.application :refer [basic-field]]
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
  (testing "link to show diff"
    (testing "no previous value"
      (is (not (contains-hiccup? :a.show-diff (basic-field {:value "foo"} "<editor-component>")))))
    (testing "has previous value"
      (is (contains-hiccup? :a.show-diff (basic-field {:value "foo", :previous-value "bar"} "<editor-component>"))))
    (testing "previous value is same as current value"
      (is (not (contains-hiccup? :a.show-diff (basic-field {:value "foo", :previous-value "foo"} "<editor-component>")))))))
