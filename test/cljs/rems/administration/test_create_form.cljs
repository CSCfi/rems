(ns rems.administration.test-create-form
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [re-frame.core :as rf]
            [rems.administration.create-form :as f :refer [build-request build-localized-string validate-form]]
            [rems.testing :refer [isolate-re-frame-state stub-re-frame-effect]]
            [rems.util :refer [getx-in]]))

(use-fixtures :each isolate-re-frame-state)

(defn reset-form []
  (rf/dispatch-sync [::f/enter-page]))

(deftest add-form-field-test
  (let [form (rf/subscribe [::f/form])]
    (testing "adds fields"
      (reset-form)
      (is (= {:fields []}
             @form)
          "before")

      (rf/dispatch-sync [::f/add-form-field])

      (is (= {:fields [{:field/type :text}]}
             @form)
          "after"))

    (testing "adds fields to the end"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/set-form-field [:fields 0 :foo] "old field"])
      (is (= {:fields [{:field/type :text :foo "old field"}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/add-form-field])

      (is (= {:fields [{:field/type :text :foo "old field"} {:field/type :text}]}
             @form)
          "after"))))

(deftest remove-form-field-test
  (let [form (rf/subscribe [::f/form])]
    (testing "removes fields"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-field])
      (is (= {:fields [{:field/type :text}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/remove-form-field 0])

      (is (= {:fields []}
             @form)
          "after"))

    (testing "removes only the field at the specified index"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/set-form-field [:fields 0 :foo] "field 0"])
      (rf/dispatch-sync [::f/set-form-field [:fields 1 :foo] "field 1"])
      (rf/dispatch-sync [::f/set-form-field [:fields 2 :foo] "field 2"])
      (is (= {:fields [{:field/type :text :foo "field 0"}
                       {:field/type :text :foo "field 1"}
                       {:field/type :text :foo "field 2"}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/remove-form-field 1])

      (is (= {:fields [{:field/type :text :foo "field 0"}
                       {:field/type :text :foo "field 2"}]}
             @form)
          "after"))))

(deftest move-form-field-up-test
  (let [form (rf/subscribe [::f/form])]
    (testing "moves fields up"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/set-form-field [:fields 0 :foo] "field 0"])
      (rf/dispatch-sync [::f/set-form-field [:fields 1 :foo] "field 1"])
      (rf/dispatch-sync [::f/set-form-field [:fields 2 :foo] "field X"])
      (is (= {:fields [{:field/type :text :foo "field 0"}
                       {:field/type :text :foo "field 1"}
                       {:field/type :text :foo "field X"}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/move-form-field-up 2])

      (is (= {:fields [{:field/type :text :foo "field 0"}
                       {:field/type :text :foo "field X"}
                       {:field/type :text :foo "field 1"}]}
             @form)
          "after move 1")

      (rf/dispatch-sync [::f/move-form-field-up 1])

      (is (= {:fields [{:field/type :text :foo "field X"}
                       {:field/type :text :foo "field 0"}
                       {:field/type :text :foo "field 1"}]}
             @form)
          "after move 2")

      (testing "unless already first"
        (rf/dispatch-sync [::f/move-form-field-up 0])

        (is (= {:fields [{:field/type :text :foo "field X"}
                         {:field/type :text :foo "field 0"}
                         {:field/type :text :foo "field 1"}]}
               @form)
            "after move 3")))))

(deftest move-form-field-down-test
  (let [form (rf/subscribe [::f/form])]
    (testing "moves fields down"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/set-form-field [:fields 0 :foo] "field X"])
      (rf/dispatch-sync [::f/set-form-field [:fields 1 :foo] "field 1"])
      (rf/dispatch-sync [::f/set-form-field [:fields 2 :foo] "field 2"])
      (is (= {:fields [{:field/type :text :foo "field X"}
                       {:field/type :text :foo "field 1"}
                       {:field/type :text :foo "field 2"}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/move-form-field-down 0])

      (is (= {:fields [{:field/type :text :foo "field 1"}
                       {:field/type :text :foo "field X"}
                       {:field/type :text :foo "field 2"}]}
             @form)
          "after move 1")

      (rf/dispatch-sync [::f/move-form-field-down 1])

      (is (= {:fields [{:field/type :text :foo "field 1"}
                       {:field/type :text :foo "field 2"}
                       {:field/type :text :foo "field X"}]}
             @form)
          "after move 2")

      (testing "unless already last"
        (rf/dispatch-sync [::f/move-form-field-down 2])

        (is (= {:fields [{:field/type :text :foo "field 1"}
                         {:field/type :text :foo "field 2"}
                         {:field/type :text :foo "field X"}]}
               @form)
            "after move 3")))))

(deftest build-request-test
  (let [form {:organization "abc"
              :title "the title"
              :fields [{:field/title {:en "en title"
                                      :fi "fi title"}
                        :field/optional true
                        :field/type :text
                        :field/max-length "12"
                        :field/placeholder {:en "en placeholder"
                                            :fi "fi placeholder"}}]}
        languages [:en :fi]]

    (testing "basic form"
      (is (= {:organization "abc"
              :title "the title"
              :fields [{:field/title {:en "en title"
                                      :fi "fi title"}
                        :field/optional true
                        :field/type :text
                        :field/max-length 12
                        :field/placeholder {:en "en placeholder"
                                            :fi "fi placeholder"}}]}
             (build-request form languages))))

    (testing "zero fields"
      (is (= {:organization "abc"
              :title "the title"
              :fields []}
             (build-request (assoc-in form [:fields] []) languages))))

    (testing "date fields"
      (let [form (assoc-in form [:fields 0 :field/type] :date)]
        (is (= {:organization "abc"
                :title "the title"
                :fields [{:field/title {:en "en title"
                                        :fi "fi title"}
                          :field/optional true
                          :field/type :date}]}
               (build-request form languages)))))

    (testing "missing optional implies false"
      (is (false? (getx-in (build-request (assoc-in form [:fields 0 :field/optional] nil) languages)
                           [:fields 0 :field/optional]))))

    (testing "placeholder is optional"
      (is (= {:en "" :fi ""}
             (getx-in (build-request (assoc-in form [:fields 0 :field/placeholder] nil) languages)
                      [:fields 0 :field/placeholder])
             (getx-in (build-request (assoc-in form [:fields 0 :field/placeholder] {:en ""}) languages)
                      [:fields 0 :field/placeholder])
             (getx-in (build-request (assoc-in form [:fields 0 :field/placeholder] {:en "" :fi ""}) languages)
                      [:fields 0 :field/placeholder]))))

    (testing "max length is optional"
      (is (nil? (getx-in (build-request (assoc-in form [:fields 0 :field/max-length] "") languages)
                         [:fields 0 :field/max-length])))
      (is (nil? (getx-in (build-request (assoc-in form [:fields 0 :field/max-length] nil) languages)
                         [:fields 0 :field/max-length]))))

    (testing "option fields"
      (let [form (-> form
                     (assoc-in [:fields 0 :field/type] :option)
                     (assoc-in [:fields 0 :field/options] [{:key "yes"
                                                            :label {:en "en yes"
                                                                    :fi "fi yes"}}
                                                           {:key "no"
                                                            :label {:en "en no"
                                                                    :fi "fi no"}}]))]
        (is (= {:organization "abc"
                :title "the title"
                :fields [{:field/title {:en "en title"
                                        :fi "fi title"}
                          :field/optional true
                          :field/type :option
                          :field/options [{:key "yes"
                                           :label {:en "en yes"
                                                   :fi "fi yes"}}
                                          {:key "no"
                                           :label {:en "en no"
                                                   :fi "fi no"}}]}]}
               (build-request form languages)))))

    (testing "multiselect fields"
      (let [form (-> form
                     (assoc-in [:fields 0 :field/type] :multiselect)
                     (assoc-in [:fields 0 :field/options] [{:key "egg"
                                                            :label {:en "Egg"
                                                                    :fi "Munaa"}}
                                                           {:key "bacon"
                                                            :label {:en "Bacon"
                                                                    :fi "Pekonia"}}]))]
        (is (= {:organization "abc"
                :title "the title"
                :fields [{:field/title {:en "en title"
                                        :fi "fi title"}
                          :field/optional true
                          :field/type :multiselect
                          :field/options [{:key "egg"
                                           :label {:en "Egg"
                                                   :fi "Munaa"}}
                                          {:key "bacon"
                                           :label {:en "Bacon"
                                                   :fi "Pekonia"}}]}]}
               (build-request form languages)))))))

(deftest validate-form-test
  (let [form {:organization "abc"
              :title "the title"
              :fields [{:field/title {:en "en title"
                                      :fi "fi title"}
                        :field/optional true
                        :field/type :text
                        :field/max-length "12"
                        :field/placeholder {:en "en placeholder"
                                            :fi "fi placeholder"}}]}
        languages [:en :fi]]

    (testing "valid form"
      (is (empty? (validate-form form languages))))

    (testing "missing organization"
      (is (= (:organization (validate-form (assoc-in form [:organization] "") languages))
             :t.form.validation/required)))

    (testing "missing title"
      (is (= (:title (validate-form (assoc-in form [:title] "") languages))
             :t.form.validation/required)))

    (testing "zero fields is ok"
      (is (empty? (validate-form (assoc-in form [:fields] []) languages))))

    (testing "missing field title"
      (let [nil-title (validate-form (assoc-in form [:fields 0 :field/title] nil) languages)]
        (is (= (get-in (validate-form (assoc-in form [:fields 0 :field/title :en] "") languages)
                       [:fields 0 :field/title :en])
               (get-in (validate-form (update-in form [:fields 0 :field/title] dissoc :en) languages)
                       [:fields 0 :field/title :en])
               (get-in nil-title [:fields 0 :field/title :en])
               (get-in nil-title [:fields 0 :field/title :fi])
               :t.form.validation/required))))

    (testing "missing field type"
      (is (get-in (validate-form (assoc-in form [:fields 0 :field/type] nil) languages)
                  [:fields 0 :field/type])
          :t.form.validation/required))

    (testing "if you use a placeholder, you must fill in all the languages"
      (is (= (get-in (validate-form (assoc-in form [:fields 0 :field/placeholder] {:en "en placeholder" :fi ""}) languages)
                     [:fields 0 :field/placeholder :fi])
             (get-in (validate-form (assoc-in form [:fields 0 :field/placeholder] {:en "en placeholder"}) languages)
                     [:fields 0 :field/placeholder :fi])
             :t.form.validation/required)))

    (testing "option fields"
      (let [form (-> form
                     (assoc-in [:fields 0 :field/type] :option)
                     (assoc-in [:fields 0 :field/options] [{:key "yes"
                                                            :label {:en "en yes"
                                                                    :fi "fi yes"}}
                                                           {:key "no"
                                                            :label {:en "en no"
                                                                    :fi "fi no"}}]))]
        (testing "valid form"
          (is (empty? (validate-form form languages))))

        (testing "missing option key"
          (is (= (get-in (validate-form (assoc-in form [:fields 0 :field/options 0 :key] "") languages)
                         [:fields 0 :field/options 0 :key])
                 (get-in (validate-form (assoc-in form [:fields 0 :field/options 0 :key] nil) languages)
                         [:fields 0 :field/options 0 :key])
                 :t.form.validation/required)))

        (testing "missing option label"
          (let [empty-label (validate-form (assoc-in form [:fields 0 :field/options 0 :label] {:en "" :fi ""}) languages)
                nil-label (validate-form (assoc-in form [:fields 0 :field/options 0 :label] nil) languages)]
            (is (= (get-in empty-label [:fields 0 :field/options 0 :label :en])
                   (get-in empty-label [:fields 0 :field/options 0 :label :fi])
                   (get-in nil-label [:fields 0 :field/options 0 :label :en])
                   (get-in nil-label [:fields 0 :field/options 0 :label :fi])
                   :t.form.validation/required))))))

    (testing "multiselect fields"
      (let [form (-> form
                     (assoc-in [:fields 0 :field/type] :multiselect)
                     (assoc-in [:fields 0 :field/options] [{:key "egg"
                                                            :label {:en "Egg"
                                                                    :fi "Munaa"}}
                                                           {:key "bacon"
                                                            :label {:en "Bacon"
                                                                    :fi "Pekonia"}}]))]
        (testing "valid form"
          (is (empty? (validate-form form languages))))

        (testing "missing option key"
          (is (= (get-in (validate-form (assoc-in form [:fields 0 :field/options 0 :key] "") languages)
                         [:fields 0 :field/options 0 :key])
                 (get-in (validate-form (assoc-in form [:fields 0 :field/options 0 :key] nil) languages)
                         [:fields 0 :field/options 0 :key])
                 :t.form.validation/required)))

        (testing "missing option label"
          (let [empty-label (validate-form (assoc-in form [:fields 0 :field/options 0 :label] {:en "" :fi ""}) languages)
                nil-label (validate-form (assoc-in form [:fields 0 :field/options 0 :label] nil) languages)]
            (is (= (get-in empty-label [:fields 0 :field/options 0 :label :en])
                   (get-in empty-label [:fields 0 :field/options 0 :label :fi])
                   (get-in nil-label [:fields 0 :field/options 0 :label :en])
                   (get-in nil-label [:fields 0 :field/options 0 :label :fi])
                   :t.form.validation/required))))))))

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
