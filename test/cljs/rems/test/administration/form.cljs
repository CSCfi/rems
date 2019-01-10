(ns rems.test.administration.form
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [re-frame.core :as rf]
            [rems.administration.form :as f :refer [build-request build-localized-string]]
            [rems.test.testing :refer [isolate-re-frame-state stub-re-frame-effect]]
            [rems.util :refer [getx-in]]))

(use-fixtures :each isolate-re-frame-state)

(defn reset-form []
  (rf/dispatch-sync [::f/enter-page]))

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

(deftest build-request-test
  (let [form {:organization "abc"
              :title "the title"
              :items [{:title {:en "en title"
                               :fi "fi title"}
                       :optional true
                       :type "text"
                       :maxlength "12"
                       :input-prompt {:en "en prompt"
                                      :fi "fi prompt"}}]}
        languages [:en :fi]]
    (testing "valid form"
      (is (= {:organization "abc"
              :title "the title"
              :items [{:title {:en "en title"
                               :fi "fi title"}
                       :optional true
                       :type "text"
                       :maxlength 12
                       :input-prompt {:en "en prompt"
                                      :fi "fi prompt"}}]}
             (build-request form languages))))

    (testing "missing organization"
      (is (nil? (build-request (assoc-in form [:organization] "") languages))))

    (testing "missing title"
      (is (nil? (build-request (assoc-in form [:title] "") languages))))

    (testing "zero items is ok"
      (is (= {:organization "abc"
              :title "the title"
              :items []}
             (build-request (assoc-in form [:items] []) languages))))

    (testing "missing item title"
      (is (= nil
             (build-request (assoc-in form [:items 0 :title :en] "") languages)
             (build-request (update-in form [:items 0 :title] dissoc :en) languages)
             (build-request (assoc-in form [:items 0 :title] nil) languages))))

    (testing "missing optional implies false"
      (is (false? (getx-in (build-request (assoc-in form [:items 0 :optional] nil) languages)
                           [:items 0 :optional]))))

    (testing "missing item type"
      (is (nil? (build-request (assoc-in form [:items 0 :type] nil) languages))))

    (testing "input prompt is optional"
      (is (= {:en "" :fi ""}
             (getx-in (build-request (assoc-in form [:items 0 :input-prompt] nil) languages)
                      [:items 0 :input-prompt])
             (getx-in (build-request (assoc-in form [:items 0 :input-prompt] {:en ""}) languages)
                      [:items 0 :input-prompt])
             (getx-in (build-request (assoc-in form [:items 0 :input-prompt] {:en "" :fi ""}) languages)
                      [:items 0 :input-prompt]))))

    (testing "maxlength is optional"
      (is (nil? (getx-in (build-request (assoc-in form [:items 0 :maxlength] "") languages)
                         [:items 0 :maxlength])))
      (is (nil? (getx-in (build-request (assoc-in form [:items 0 :maxlength] nil) languages)
                         [:items 0 :maxlength]))))

    (testing "if you use input prompt, you must fill in all the languages"
      (is (= nil
             (build-request (assoc-in form [:items 0 :input-prompt] {:en "en prompt" :fi ""}) languages)
             (build-request (assoc-in form [:items 0 :input-prompt] {:en "en prompt"}) languages))))

    (testing "date fields"
      (let [form (assoc-in form [:items 0 :type] "date")]

        (testing "valid form"
          (is (= {:organization "abc"
                  :title "the title"
                  :items [{:title {:en "en title"
                                   :fi "fi title"}
                           :optional true
                           :type "date"}]}
                 (build-request form languages))))))

    (testing "option fields"
      (let [form (-> form
                     (assoc-in [:items 0 :type] "option")
                     (assoc-in [:items 0 :options] [{:key "yes"
                                                     :label {:en "en yes"
                                                             :fi "fi yes"}}
                                                    {:key "no"
                                                     :label {:en "en no"
                                                             :fi "fi no"}}]))]

        (testing "valid form"
          (is (= {:organization "abc"
                  :title "the title"
                  :items [{:title {:en "en title"
                                   :fi "fi title"}
                           :optional true
                           :type "option"
                           :options [{:key "yes"
                                      :label {:en "en yes"
                                              :fi "fi yes"}}
                                     {:key "no"
                                      :label {:en "en no"
                                              :fi "fi no"}}]}]}
                 (build-request form languages))))

        (testing "missing option key"
          (is (= nil
                 (build-request (assoc-in form [:items 0 :options 0 :key] "") languages)
                 (build-request (assoc-in form [:items 0 :options 0 :key] nil) languages))))

        (testing "missing option label"
          (is (= nil
                 (build-request (assoc-in form [:items 0 :options 0 :label] {:en "" :fi ""}) languages)
                 (build-request (assoc-in form [:items 0 :options 0 :label] nil) languages))))))

    (testing "multiselect fields"
      (let [form (-> form
                     (assoc-in [:items 0 :type] "multiselect")
                     (assoc-in [:items 0 :options] [{:key "egg"
                                                     :label {:en "Egg"
                                                             :fi "Munaa"}}
                                                    {:key "bacon"
                                                     :label {:en "Bacon"
                                                             :fi "Pekonia"}}]))]

        (testing "valid form"
          (is (= {:organization "abc"
                  :title "the title"
                  :items [{:title {:en "en title"
                                   :fi "fi title"}
                           :optional true
                           :type "multiselect"
                           :options [{:key "egg"
                                      :label {:en "Egg"
                                              :fi "Munaa"}}
                                     {:key "bacon"
                                      :label {:en "Bacon"
                                              :fi "Pekonia"}}]}]}
                 (build-request form languages))))

        (testing "missing option key"
          (is (= nil
                 (build-request (assoc-in form [:items 0 :options 0 :key] "") languages)
                 (build-request (assoc-in form [:items 0 :options 0 :key] nil) languages))))

        (testing "missing option label"
          (is (= nil
                 (build-request (assoc-in form [:items 0 :options 0 :label] {:en "" :fi ""}) languages)
                 (build-request (assoc-in form [:items 0 :options 0 :label] nil) languages))))))))

(deftest build-localized-string-test
  (let [languages [:en :fi]]
    (testing "localizations are copied as-is"
      (is (= {:en "x", :fi "y"}
             (build-localized-string {:en "x", :fi "y"} languages))))
    (testing "missing localizations default to empty string"
      (is (= {:en "", :fi ""}
             (build-localized-string {} languages))))
    (testing "additional languages are excluded"
      (is (= {:en "x", :fi "y"}
             (build-localized-string {:en "x", :fi "y", :sv "z"} languages))))))
