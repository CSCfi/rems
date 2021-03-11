(ns rems.test-table
  (:require [clojure.test :refer [deftest is testing]]
            [rems.table :refer [apply-row-defaults parse-search-terms]]))

(deftest test-apply-row-defaults
  (testing "all custom"
    (is (= {:key 123
            :foo {:sort-value "foo1"
                  :display-value "foo2"
                  :filter-value "foo3"
                  :td [:td "foo4"]}}
           (apply-row-defaults {:key 123
                                :foo {:sort-value "foo1"
                                      :display-value "foo2"
                                      :filter-value "foo3"
                                      :td [:td "foo4"]}}))))
  (testing "all defaults, string value"
    (is (= {:key 123
            :foo {:value "foo"
                  :sort-value "foo"
                  :display-value "foo"
                  :filter-value "foo"
                  :td [:td {:class "foo"} "foo"]}}
           (apply-row-defaults {:key 123
                                :foo {:value "foo"}}))))
  (testing "all defaults, non-string value"
    (is (= {:key 123
            :foo {:value 42
                  :sort-value 42
                  :display-value "42"
                  :filter-value "42"
                  :td [:td {:class "foo"} "42"]}}
           (apply-row-defaults {:key 123
                                :foo {:value 42}}))))
  (testing "component only"
    (is (= {:key 123
            :foo {:sort-value nil
                  :display-value ""
                  :filter-value ""
                  :td [:td.foo [:button "Button"]]}}
           (apply-row-defaults {:key 123
                                :foo {:td [:td.foo [:button "Button"]]}}))))
  (testing ":value is normalized to lowercase in :sort-value"
    (is (= {:key 123
            :foo {:value "FooBar"
                  :sort-value "foobar"
                  :display-value "FooBar"
                  :filter-value ""
                  :td [:td ""]}}
           (apply-row-defaults {:key 123
                                :foo {:value "FooBar"
                                      :filter-value ""
                                      :td [:td ""]}}))))
  (testing ":sort-value retains non-lowercase string if explicitly set"
    (is (= {:key 123
            :foo {:value "FooBar"
                  :sort-value "FooBar"
                  :display-value "FooBar"
                  :filter-value ""
                  :td [:td ""]}}
           (apply-row-defaults {:key 123
                                :foo {:value "FooBar"
                                      :sort-value "FooBar"
                                      :filter-value ""
                                      :td [:td ""]}}))))
  (testing ":value is normalized to lowercase in :filter-value"
    (is (= {:key 123
            :foo {:sort-value ""
                  :display-value "FooBar"
                  :filter-value "foobar"
                  :td [:td ""]}}
           (apply-row-defaults {:key 123
                                :foo {:sort-value ""
                                      :display-value "FooBar"
                                      :td [:td ""]}}))))
  (testing ":filter-value retains non-lowercase string if explicitly set"
    (is (= {:key 123
            :foo {:sort-value ""
                  :display-value "FooBar"
                  :filter-value "FooBar"
                  :td [:td ""]}}
           (apply-row-defaults {:key 123
                                :foo {:sort-value ""
                                      :display-value "FooBar"
                                      :filter-value "FooBar"
                                      :td [:td ""]}}))))
  (testing "cannot calculate :filter-value from non-string :display-value"
    (is (= {:key 123
            :foo {:sort-value ""
                  :display-value [:p "foo"]
                  :filter-value ""
                  :td [:td ""]}}
           (apply-row-defaults {:key 123
                                :foo {:sort-value ""
                                      :display-value [:p "foo"]
                                      :td [:td ""]}})))))

(deftest test-parse-search-terms
  (is (= [] (parse-search-terms nil)))
  (is (= [] (parse-search-terms "")))
  (is (= ["word"] (parse-search-terms "word")))
  (is (= ["uppercase"] (parse-search-terms "UPPERCASE")))
  (is (= ["two" "words"] (parse-search-terms "two words")))
  (is (= ["white" "space"] (parse-search-terms "   white \t\n space  "))))
