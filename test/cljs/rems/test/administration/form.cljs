(ns rems.test.administration.form
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [re-frame.core :as rf]
            [rems.administration.form :as f]
            [rems.test.testing :refer [isolate-re-frame-state]]))

(use-fixtures :each isolate-re-frame-state)

(defn reset-form []
  (rf/dispatch-sync [::f/reset-create-form]))

(deftest add-form-item-test
  (let [form (rf/subscribe [::f/form])]
    (testing "adds items"
      (reset-form)
      (is (= {:items []}
             @form)
          "before")

      (rf/dispatch-sync [::f/add-form-item])

      (is (= {:items [{}]}
             @form)
          "after"))

    (testing "adds items to the end"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-item])
      (rf/dispatch-sync [::f/set-form-field [:items 0 :foo] "old item"])
      (is (= {:items [{:foo "old item"}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/add-form-item])

      (is (= {:items [{:foo "old item"} {}]}
             @form)
          "after"))))

(deftest remove-form-item-test
  (let [form (rf/subscribe [::f/form])]
    (testing "removes items"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-item])
      (is (= {:items [{}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/remove-form-item 0])

      (is (= {:items []}
             @form)
          "after"))

    (testing "removes only the item at the specified index"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-item])
      (rf/dispatch-sync [::f/add-form-item])
      (rf/dispatch-sync [::f/add-form-item])
      (rf/dispatch-sync [::f/set-form-field [:items 0 :foo] "item 0"])
      (rf/dispatch-sync [::f/set-form-field [:items 1 :foo] "item 1"])
      (rf/dispatch-sync [::f/set-form-field [:items 2 :foo] "item 2"])
      (is (= {:items [{:foo "item 0"}
                      {:foo "item 1"}
                      {:foo "item 2"}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/remove-form-item 1])

      (is (= {:items [{:foo "item 0"}
                      {:foo "item 2"}]}
             @form)
          "after"))))

(deftest move-form-item-up-test
  (let [form (rf/subscribe [::f/form])]
    (testing "moves items up"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-item])
      (rf/dispatch-sync [::f/add-form-item])
      (rf/dispatch-sync [::f/add-form-item])
      (rf/dispatch-sync [::f/set-form-field [:items 0 :foo] "item 0"])
      (rf/dispatch-sync [::f/set-form-field [:items 1 :foo] "item 1"])
      (rf/dispatch-sync [::f/set-form-field [:items 2 :foo] "item X"])
      (is (= {:items [{:foo "item 0"}
                      {:foo "item 1"}
                      {:foo "item X"}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/move-form-item-up 2])

      (is (= {:items [{:foo "item 0"}
                      {:foo "item X"}
                      {:foo "item 1"}]}
             @form)
          "after move 1")

      (rf/dispatch-sync [::f/move-form-item-up 1])

      (is (= {:items [{:foo "item X"}
                      {:foo "item 0"}
                      {:foo "item 1"}]}
             @form)
          "after move 2")

      (testing "unless already first"
        (rf/dispatch-sync [::f/move-form-item-up 0])

        (is (= {:items [{:foo "item X"}
                        {:foo "item 0"}
                        {:foo "item 1"}]}
               @form)
            "after move 3")))))

(deftest move-form-item-down-test
  (let [form (rf/subscribe [::f/form])]
    (testing "moves items down"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-item])
      (rf/dispatch-sync [::f/add-form-item])
      (rf/dispatch-sync [::f/add-form-item])
      (rf/dispatch-sync [::f/set-form-field [:items 0 :foo] "item X"])
      (rf/dispatch-sync [::f/set-form-field [:items 1 :foo] "item 1"])
      (rf/dispatch-sync [::f/set-form-field [:items 2 :foo] "item 2"])
      (is (= {:items [{:foo "item X"}
                      {:foo "item 1"}
                      {:foo "item 2"}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/move-form-item-down 0])

      (is (= {:items [{:foo "item 1"}
                      {:foo "item X"}
                      {:foo "item 2"}]}
             @form)
          "after move 1")

      (rf/dispatch-sync [::f/move-form-item-down 1])

      (is (= {:items [{:foo "item 1"}
                      {:foo "item 2"}
                      {:foo "item X"}]}
             @form)
          "after move 2")

      (testing "unless already last"
        (rf/dispatch-sync [::f/move-form-item-down 2])

        (is (= {:items [{:foo "item 1"}
                        {:foo "item 2"}
                        {:foo "item X"}]}
               @form)
            "after move 3")))))
