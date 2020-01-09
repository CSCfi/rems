(ns rems.administration.test-create-form
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [re-frame.core :as rf]
            [rems.administration.create-form :refer [build-request build-localized-string validate-form]]
            [rems.testing :refer [isolate-re-frame-state stub-re-frame-effect]]
            [rems.util :refer [getx-in]]))

(defn- stable-id-fixture [f]
  (let [id-atom (atom 0)]
    (with-redefs [rems.administration.create-form/generate-stable-id (fn [] (str "stable" (swap! id-atom inc)))]
      (f))))

(use-fixtures
  :each
  isolate-re-frame-state
  stable-id-fixture)

(defn reset-form []
  (rf/dispatch-sync [:rems.administration.create-form/enter-page]))

(deftest add-form-field-test
  (let [form (rf/subscribe [:rems.administration.create-form/form])]
    (testing "adds fields"
      (reset-form)
      (is (= {:form/fields []}
             @form)
          "before")

      (rf/dispatch-sync [:rems.administration.create-form/add-form-field])

      (is (= {:form/fields [{:field/stable-id "stable1" :field/id 0 :field/type :text}]}
             @form)
          "after"))

    (testing "adds fields to the end"
      (reset-form)
      (rf/dispatch-sync [:rems.administration.create-form/add-form-field])
      (rf/dispatch-sync [:rems.administration.create-form/set-form-field [:form/fields 0 :foo] "old field"])
      (is (= {:form/fields [{:field/stable-id "stable2" :field/id 0 :field/type :text :foo "old field"}]}
             @form)
          "before")

      (rf/dispatch-sync [:rems.administration.create-form/add-form-field])

      (is (= {:form/fields [{:field/stable-id "stable2" :field/id 0 :field/type :text :foo "old field"}
                            {:field/stable-id "stable3" :field/id 1 :field/type :text}]}
             @form)
          "after"))))

(deftest remove-form-field-test
  (let [form (rf/subscribe [:rems.administration.create-form/form])]
    (testing "removes fields"
      (reset-form)
      (rf/dispatch-sync [:rems.administration.create-form/add-form-field])
      (is (= {:form/fields [{:field/stable-id "stable1" :field/id 0 :field/type :text}]}
             @form)
          "before")

      (rf/dispatch-sync [:rems.administration.create-form/remove-form-field 0])

      (is (= {:form/fields []}
             @form)
          "after"))

    (testing "removes only the field at the specified index"
      (reset-form)
      (rf/dispatch-sync [:rems.administration.create-form/add-form-field])
      (rf/dispatch-sync [:rems.administration.create-form/add-form-field])
      (rf/dispatch-sync [:rems.administration.create-form/add-form-field])
      (rf/dispatch-sync [:rems.administration.create-form/set-form-field [:form/fields 0 :foo] "field 0"])
      (rf/dispatch-sync [:rems.administration.create-form/set-form-field [:form/fields 1 :foo] "field 1"])
      (rf/dispatch-sync [:rems.administration.create-form/set-form-field [:form/fields 2 :foo] "field 2"])
      (is (= {:form/fields [{:field/stable-id "stable2" :field/id 0 :field/type :text :foo "field 0"}
                            {:field/stable-id "stable3" :field/id 1 :field/type :text :foo "field 1"}
                            {:field/stable-id "stable4" :field/id 2 :field/type :text :foo "field 2"}]}
             @form)
          "before")

      (rf/dispatch-sync [:rems.administration.create-form/remove-form-field 1])

      (is (= {:form/fields [{:field/stable-id "stable2" :field/id 0 :field/type :text :foo "field 0"}
                            {:field/stable-id "stable4" :field/id 1 :field/type :text :foo "field 2"}]}
             @form)
          "after"))))

(deftest move-form-field-up-test
  (let [form (rf/subscribe [:rems.administration.create-form/form])]
    (testing "moves fields up"
      (reset-form)
      (rf/dispatch-sync [:rems.administration.create-form/add-form-field])
      (rf/dispatch-sync [:rems.administration.create-form/add-form-field])
      (rf/dispatch-sync [:rems.administration.create-form/add-form-field])
      (rf/dispatch-sync [:rems.administration.create-form/set-form-field [:form/fields 0 :foo] "field 0"])
      (rf/dispatch-sync [:rems.administration.create-form/set-form-field [:form/fields 1 :foo] "field 1"])
      (rf/dispatch-sync [:rems.administration.create-form/set-form-field [:form/fields 2 :foo] "field X"])
      (is (= {:form/fields [{:field/stable-id "stable1" :field/id 0 :field/type :text :foo "field 0"}
                            {:field/stable-id "stable2" :field/id 1 :field/type :text :foo "field 1"}
                            {:field/stable-id "stable3" :field/id 2 :field/type :text :foo "field X"}]}
             @form)
          "before")

      (rf/dispatch-sync [:rems.administration.create-form/move-form-field-up 2])

      (is (= {:form/fields [{:field/stable-id "stable1" :field/id 0 :field/type :text :foo "field 0"}
                            {:field/stable-id "stable3" :field/id 1 :field/type :text :foo "field X"}
                            {:field/stable-id "stable2" :field/id 2 :field/type :text :foo "field 1"}]}
             @form)
          "after move 1")

      (rf/dispatch-sync [:rems.administration.create-form/move-form-field-up 1])

      (is (= {:form/fields [{:field/stable-id "stable3" :field/id 0 :field/type :text :foo "field X"}
                            {:field/stable-id "stable1" :field/id 1 :field/type :text :foo "field 0"}
                            {:field/stable-id "stable2" :field/id 2 :field/type :text :foo "field 1"}]}
             @form)
          "after move 2")

      (testing "unless already first"
        (rf/dispatch-sync [:rems.administration.create-form/move-form-field-up 0])

        (is (= {:form/fields [{:field/stable-id "stable3" :field/id 0 :field/type :text :foo "field X"}
                              {:field/stable-id "stable1" :field/id 1 :field/type :text :foo "field 0"}
                              {:field/stable-id "stable2" :field/id 2 :field/type :text :foo "field 1"}]}
               @form)
            "after move 3")))))

(deftest move-form-field-down-test
  (let [form (rf/subscribe [:rems.administration.create-form/form])]
    (testing "moves fields down"
      (reset-form)
      (rf/dispatch-sync [:rems.administration.create-form/add-form-field])
      (rf/dispatch-sync [:rems.administration.create-form/add-form-field])
      (rf/dispatch-sync [:rems.administration.create-form/add-form-field])
      (rf/dispatch-sync [:rems.administration.create-form/set-form-field [:form/fields 0 :foo] "field X"])
      (rf/dispatch-sync [:rems.administration.create-form/set-form-field [:form/fields 1 :foo] "field 1"])
      (rf/dispatch-sync [:rems.administration.create-form/set-form-field [:form/fields 2 :foo] "field 2"])
      (is (= {:form/fields [{:field/stable-id "stable1" :field/id 0 :field/type :text :foo "field X"}
                            {:field/stable-id "stable2" :field/id 1 :field/type :text :foo "field 1"}
                            {:field/stable-id "stable3" :field/id 2 :field/type :text :foo "field 2"}]}
             @form)
          "before")

      (rf/dispatch-sync [:rems.administration.create-form/move-form-field-down 0])

      (is (= {:form/fields [{:field/stable-id "stable2" :field/id 0 :field/type :text :foo "field 1"}
                            {:field/stable-id "stable1" :field/id 1 :field/type :text :foo "field X"}
                            {:field/stable-id "stable3" :field/id 2 :field/type :text :foo "field 2"}]}
             @form)
          "after move 1")

      (rf/dispatch-sync [:rems.administration.create-form/move-form-field-down 1])

      (is (= {:form/fields [{:field/stable-id "stable2" :field/id 0 :field/type :text :foo "field 1"}
                            {:field/stable-id "stable3" :field/id 1 :field/type :text :foo "field 2"}
                            {:field/stable-id "stable1" :field/id 2 :field/type :text :foo "field X"}]}
             @form)
          "after move 2")

      (testing "unless already last"
        (rf/dispatch-sync [:rems.administration.create-form/move-form-field-down 2])

        (is (= {:form/fields [{:field/stable-id "stable2" :field/id 0 :field/type :text :foo "field 1"}
                              {:field/stable-id "stable3" :field/id 1 :field/type :text :foo "field 2"}
                              {:field/stable-id "stable1" :field/id 2 :field/type :text :foo "field X"}]}
               @form)
            "after move 3")))))

(deftest build-request-test
  (let [form {:form/organization "abc"
              :form/title "the title"
              :form/fields [{:field/stable-id "stable999"
                             :field/title {:en "en title"
                                           :fi "fi title"}
                             :field/optional true
                             :field/type :text
                             :field/max-length "12"
                             :field/placeholder {:en "en placeholder"
                                                 :fi "fi placeholder"}}]}
        languages [:en :fi]]

    (testing "basic form"
      (is (= {:form/organization "abc"
              :form/title "the title"
              :form/fields [{:field/title {:en "en title"
                                           :fi "fi title"}
                             :field/optional true
                             :field/type :text
                             :field/max-length 12
                             :field/placeholder {:en "en placeholder"
                                                 :fi "fi placeholder"}}]}
             (build-request form languages))))

    (testing "trim strings"
      (is (= {:form/organization "abc"
              :form/title "the title"
              :form/fields [{:field/title {:en "en title"
                                           :fi "fi title"}
                             :field/optional true
                             :field/type :text
                             :field/max-length 12
                             :field/placeholder {:en "en placeholder"
                                                 :fi "fi placeholder"}}]}
             (build-request (assoc form :form/organization " abc\t\n") languages))))

    (testing "zero fields"
      (is (= {:form/organization "abc"
              :form/title "the title"
              :form/fields []}
             (build-request (assoc-in form [:form/fields] []) languages))))

    (testing "date fields"
      (let [form (assoc-in form [:form/fields 0 :field/type] :date)]
        (is (= {:form/organization "abc"
                :form/title "the title"
                :form/fields [{:field/title {:en "en title"
                                             :fi "fi title"}
                               :field/optional true
                               :field/type :date}]}
               (build-request form languages)))))

    (testing "missing optional implies false"
      (is (false? (getx-in (build-request (assoc-in form [:form/fields 0 :field/optional] nil) languages)
                           [:form/fields 0 :field/optional]))))

    (testing "placeholder is optional"
      (is (= {:en "" :fi ""}
             (getx-in (build-request (assoc-in form [:form/fields 0 :field/placeholder] nil) languages)
                      [:form/fields 0 :field/placeholder])
             (getx-in (build-request (assoc-in form [:form/fields 0 :field/placeholder] {:en ""}) languages)
                      [:form/fields 0 :field/placeholder])
             (getx-in (build-request (assoc-in form [:form/fields 0 :field/placeholder] {:en "" :fi ""}) languages)
                      [:form/fields 0 :field/placeholder]))))

    (testing "max length is optional"
      (is (nil? (getx-in (build-request (assoc-in form [:form/fields 0 :field/max-length] "") languages)
                         [:form/fields 0 :field/max-length])))
      (is (nil? (getx-in (build-request (assoc-in form [:form/fields 0 :field/max-length] nil) languages)
                         [:form/fields 0 :field/max-length]))))

    (testing "option fields"
      (let [form (-> form
                     (assoc-in [:form/fields 0 :field/type] :option)
                     (assoc-in [:form/fields 0 :field/options] [{:key "yes"
                                                                 :label {:en "en yes"
                                                                         :fi "fi yes"}}
                                                                {:key "no"
                                                                 :label {:en "en no"
                                                                         :fi "fi no"}}]))]
        (is (= {:form/organization "abc"
                :form/title "the title"
                :form/fields [{:field/title {:en "en title"
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
                     (assoc-in [:form/fields 0 :field/type] :multiselect)
                     (assoc-in [:form/fields 0 :field/options] [{:key "egg"
                                                                 :label {:en "Egg"
                                                                         :fi "Munaa"}}
                                                                {:key "bacon"
                                                                 :label {:en "Bacon"
                                                                         :fi "Pekonia"}}]))]
        (is (= {:form/organization "abc"
                :form/title "the title"
                :form/fields [{:field/title {:en "en title"
                                             :fi "fi title"}
                               :field/optional true
                               :field/type :multiselect
                               :field/options [{:key "egg"
                                                :label {:en "Egg"
                                                        :fi "Munaa"}}
                                               {:key "bacon"
                                                :label {:en "Bacon"
                                                        :fi "Pekonia"}}]}]}
               (build-request form languages)))))

    (testing "visibility"
      (let [form (-> form
                     (assoc-in [:form/fields 0 :field/id] 0)
                     (assoc-in [:form/fields 0 :field/type] :option)
                     (assoc-in [:form/fields 0 :field/options] [{:key "yes"
                                                                 :label {:en "en yes"
                                                                         :fi "fi yes"}}
                                                                {:key "no"
                                                                 :label {:en "en no"
                                                                         :fi "fi no"}}])
                     (assoc-in [:form/fields 1] {:field/id 1
                                                 :field/stable-id "stable9999"
                                                 :field/title {:en "en title additional"
                                                               :fi "fi title additional"}
                                                 :field/optional false
                                                 :field/type :text
                                                 :field/max-length "12"
                                                 :field/placeholder {:en "en placeholder"
                                                                     :fi "fi placeholder"}}))]

        (testing "default"
          (is (nil? (get-in (build-request (assoc-in form [:form/fields 1 :field/visible] {:visible/type :always}) languages)
                            [:form/fields 1 :field/visible]))
              "always is the default so nothing needs to be included")
          (is (= {:visible/type :only-if
                  :visible/field nil
                  :visible/value nil}
                 (getx-in (build-request (assoc-in form
                                                   [:form/fields 1 :field/visible]
                                                   {:visible/type :only-if})
                                         languages)
                          [:form/fields 1 :field/visible]))
              "missing data is nil"))

        (testing "correct data"
          (is (= {:field/title {:en "en title additional"
                                :fi "fi title additional"}
                  :field/optional false
                  :field/type :text
                  :field/max-length 12
                  :field/placeholder {:en "en placeholder"
                                      :fi "fi placeholder"}
                  :field/visible {:visible/type :only-if
                                  :visible/field {:field/id 0}
                                  :visible/value "yes"}}
                 (getx-in (build-request (assoc-in form
                                                   [:form/fields 1 :field/visible]
                                                   {:visible/type :only-if
                                                    :visible/field {:field/id 0}
                                                    :visible/value "yes"})
                                         languages)
                          [:form/fields 1]))))))))

(deftest validate-form-test
  (let [form {:form/organization "abc"
              :form/title "the title"
              :form/fields [{:field/title {:en "en title"
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
      (is (= (:form/organization (validate-form (assoc-in form [:form/organization] "") languages))
             :t.form.validation/required)))

    (testing "missing title"
      (is (= (:form/title (validate-form (assoc-in form [:form/title] "") languages))
             :t.form.validation/required)))

    (testing "zero fields is ok"
      (is (empty? (validate-form (assoc-in form [:form/fields] []) languages))))

    (testing "missing field title"
      (let [nil-title (validate-form (assoc-in form [:form/fields 0 :field/title] nil) languages)]
        (is (= (get-in (validate-form (assoc-in form [:form/fields 0 :field/title :en] "") languages)
                       [:form/fields 0 :field/title :en])
               (get-in (validate-form (update-in form [:form/fields 0 :field/title] dissoc :en) languages)
                       [:form/fields 0 :field/title :en])
               (get-in nil-title [:form/fields 0 :field/title :en])
               (get-in nil-title [:form/fields 0 :field/title :fi])
               :t.form.validation/required))))

    (testing "missing field type"
      (is (get-in (validate-form (assoc-in form [:form/fields 0 :field/type] nil) languages)
                  [:form/fields 0 :field/type])
          :t.form.validation/required))

    (testing "if you use a placeholder, you must fill in all the languages"
      (is (= (get-in (validate-form (assoc-in form [:form/fields 0 :field/placeholder] {:en "en placeholder" :fi ""}) languages)
                     [:form/fields 0 :field/placeholder :fi])
             (get-in (validate-form (assoc-in form [:form/fields 0 :field/placeholder] {:en "en placeholder"}) languages)
                     [:form/fields 0 :field/placeholder :fi])
             :t.form.validation/required)))

    (testing "placeholder is not validated if it is not used"
      (let [form (-> form
                     (assoc-in [:form/fields 0 :field/type] :label)
                     (assoc-in [:form/fields 0 :field/placeholder :fi] ""))]
        (is (empty? (validate-form form)))))

    (testing "option fields"
      (let [form (-> form
                     (assoc-in [:form/fields 0 :field/type] :option)
                     (assoc-in [:form/fields 0 :field/options] [{:key "yes"
                                                                 :label {:en "en yes"
                                                                         :fi "fi yes"}}
                                                                {:key "no"
                                                                 :label {:en "en no"
                                                                         :fi "fi no"}}]))]
        (testing "valid form"
          (is (empty? (validate-form form languages))))

        (testing "missing option key"
          (is (= (get-in (validate-form (assoc-in form [:form/fields 0 :field/options 0 :key] "") languages)
                         [:form/fields 0 :field/options 0 :key])
                 (get-in (validate-form (assoc-in form [:form/fields 0 :field/options 0 :key] nil) languages)
                         [:form/fields 0 :field/options 0 :key])
                 :t.form.validation/required)))

        (testing "... are not validated when options are not used"
          (let [form (-> form
                         (assoc-in [:form/fields 0 :field/options 0 :key] "")
                         (assoc-in [:form/fields 0 :field/type] :texta))]
            (is (empty? (validate-form form)))))

        (testing "missing option label"
          (let [empty-label (validate-form (assoc-in form [:form/fields 0 :field/options 0 :label] {:en "" :fi ""}) languages)
                nil-label (validate-form (assoc-in form [:form/fields 0 :field/options 0 :label] nil) languages)]
            (is (= (get-in empty-label [:form/fields 0 :field/options 0 :label :en])
                   (get-in empty-label [:form/fields 0 :field/options 0 :label :fi])
                   (get-in nil-label [:form/fields 0 :field/options 0 :label :en])
                   (get-in nil-label [:form/fields 0 :field/options 0 :label :fi])
                   :t.form.validation/required))))))

    (testing "multiselect fields"
      (let [form (-> form
                     (assoc-in [:form/fields 0 :field/type] :multiselect)
                     (assoc-in [:form/fields 0 :field/options] [{:key "egg"
                                                                 :label {:en "Egg"
                                                                         :fi "Munaa"}}
                                                                {:key "bacon"
                                                                 :label {:en "Bacon"
                                                                         :fi "Pekonia"}}]))]
        (testing "valid form"
          (is (empty? (validate-form form languages))))

        (testing "missing option key"
          (is (= (get-in (validate-form (assoc-in form [:form/fields 0 :field/options 0 :key] "") languages)
                         [:form/fields 0 :field/options 0 :key])
                 (get-in (validate-form (assoc-in form [:form/fields 0 :field/options 0 :key] nil) languages)
                         [:form/fields 0 :field/options 0 :key])
                 :t.form.validation/required)))

        (testing "missing option label"
          (let [empty-label (validate-form (assoc-in form [:form/fields 0 :field/options 0 :label] {:en "" :fi ""}) languages)
                nil-label (validate-form (assoc-in form [:form/fields 0 :field/options 0 :label] nil) languages)]
            (is (= (get-in empty-label [:form/fields 0 :field/options 0 :label :en])
                   (get-in empty-label [:form/fields 0 :field/options 0 :label :fi])
                   (get-in nil-label [:form/fields 0 :field/options 0 :label :en])
                   (get-in nil-label [:form/fields 0 :field/options 0 :label :fi])
                   :t.form.validation/required))))))

    (testing "visible"
      (let [form (-> form
                     (assoc-in [:form/fields 0 :field/id] 0)
                     (assoc-in [:form/fields 0 :field/type] :option)
                     (assoc-in [:form/fields 0 :field/options] [{:key "yes"
                                                                 :label {:en "en yes"
                                                                         :fi "fi yes"}}
                                                                {:key "no"
                                                                 :label {:en "en no"
                                                                         :fi "fi no"}}])
                     (assoc-in [:form/fields 1] {:field/id 1
                                                 :field/stable-id "stable9999"
                                                 :field/title {:en "en title additional"
                                                               :fi "fi title additional"}
                                                 :field/optional false
                                                 :field/type :text
                                                 :field/max-length "12"
                                                 :field/placeholder {:en "en placeholder"
                                                                     :fi "fi placeholder"}}))
            validate-visible (fn [visible]
                               (validate-form (assoc-in form [:form/fields 1 :field/visible] visible) languages))]

        (testing "invalid type"
          (is (= (getx-in (validate-visible {:visible/type nil})
                          [:form/fields 1 :field/visible :visible/type])
                 :t.form.validation/required))
          (is (= (getx-in (validate-visible {:visible/type :does-not-exist})
                          [:form/fields 1 :field/visible :visible/type])
                 :t.form.validation/invalid-value)))

        (testing "invalid field"
          (is (= (getx-in (validate-visible {:visible/type :only-if})
                          [:form/fields 1 :field/visible :visible/field])
                 (getx-in (validate-visible {:visible/type :only-if
                                             :visible/field nil})
                          [:form/fields 1 :field/visible :visible/field])
                 :t.form.validation/required))
          (is (= (getx-in (validate-visible {:visible/type :only-if
                                             :visible/field {}})
                          [:form/fields 1 :field/visible :visible/field])
                 :t.form.validation/invalid-value)))

        (testing "invalid value"
          (is (= (getx-in (validate-visible {:visible/type :only-if
                                             :visible/field {:field/id 0}})
                          [:form/fields 1 :field/visible :visible/value])
                 :t.form.validation/required))
          (is (= (getx-in (validate-visible {:visible/type :only-if
                                             :visible/field {:field/id 0}
                                             :visible/value "does-not-exist"})
                          [:form/fields 1 :field/visible :visible/value])
                 :t.form.validation/invalid-value)))

        (testing "correct data"
          (is (empty? (validate-visible {:visible/type :always})))
          (is (empty? (validate-visible {:visible/type :only-if
                                         :visible/field {:field/id 0}
                                         :visible/value ["yes"]}))))))))

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
