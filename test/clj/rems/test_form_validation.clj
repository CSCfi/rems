(ns rems.test-form-validation
  (:refer-clojure :exclude [validate-fields])
  (:require [clojure.test :refer :all]
            [rems.form-validation :refer [validate-fields-for-submit validate-fields-for-draft]]))

(deftest test-validate-fields-for-submit
  (testing "all fields filled"
    (is (nil? (validate-fields-for-submit [{:field/title    "A"
                                            :field/optional false
                                            :field/visible  true
                                            :field/value    "a"}]))))


  (testing "optional fields do not need to be filled"
    (is (nil? (validate-fields-for-submit [{:field/title    "A"
                                            :field/optional true
                                            :field/visible  true
                                            :field/value    nil}
                                           {:field/title    "B"
                                            :field/optional true
                                            :field/visible  true
                                            :field/value    ""}]))))

  (testing "invisible fields do not need to be filled"
    (is (nil? (validate-fields-for-submit [{:field/title    "A"
                                            :field/optional true
                                            :field/visible  false
                                            :field/value    nil}
                                           {:field/title    "B"
                                            :field/optional true
                                            :field/visible  false
                                            :field/value    ""}]))))

  (testing "labels and headers are always effectively optional"
    (is (nil? (validate-fields-for-submit [{:field/type     :label
                                            :field/optional false
                                            :field/visible  true
                                            :field/value    ""}
                                           {:field/type     :label
                                            :field/optional true
                                            :field/visible  true
                                            :field/value    ""}
                                           {:field/type     :header
                                            :field/optional false
                                            :field/visible  true
                                            :field/value    ""}
                                           {:field/type     :header
                                            :field/optional true
                                            :field/visible  true
                                            :field/value    ""}]))))

  (testing "error: field required"
    (is (= [{:type :t.form.validation/required :field-id "2"}
            {:type :t.form.validation/required :field-id "3"}
            {:type :t.form.validation/required :field-id "4"}]
           (validate-fields-for-submit [{:field/id       "1"
                                         :field/optional true
                                         :field/visible  true
                                         :field/value    nil}
                                        {:field/id       "2"
                                         :field/optional false
                                         :field/visible  true
                                         :field/value    nil}
                                        {:field/id       "3"
                                         :field/optional false
                                         :field/visible  true
                                         :field/value    ""}
                                        {:field/id       "4"
                                         :field/optional false
                                         :field/visible  true
                                         :field/value    "    "}]))))

  (testing "error: field input too long"
    (is (= [{:type :t.form.validation/toolong :field-id "2"}]
           (validate-fields-for-submit [{:field/id         "1"
                                         :field/max-length 5
                                         :field/visible    true
                                         :field/value      "abcde"}
                                        {:field/id         "2"
                                         :field/max-length 5
                                         :field/visible    true
                                         :field/value      "abcdef"}]))))

  (testing "error: field input selected option is invalid"
    (is (= [{:type :t.form.validation/invalid-value :field-id "1"}]
           (validate-fields-for-submit [{:field/id       "1"
                                         :field/title    {:en "Option list."
                                                          :fi "Valintalista."}
                                         :field/type     :option
                                         :field/options  [{:key   "Option1"
                                                           :label {:en "First"
                                                                   :fi "Ensimmäinen"}}
                                                          {:key   "Option2"
                                                           :label {:en "Second"
                                                                   :fi "Toinen"}}
                                                          {:key   "Option3"
                                                           :label {:en "Third"
                                                                   :fi "Kolmas "}}]
                                         :field/optional true
                                         :field/visible  true
                                         :field/value    "foobar"}
                                        {:field/id       "2"
                                         :field/title    {:en "Option list."
                                                          :fi "Valintalista."}
                                         :field/type     :option
                                         :field/options  [{:key   "Option1"
                                                           :label {:en "First"
                                                                   :fi "Ensimmäinen"}}
                                                          {:key   "Option2"
                                                           :label {:en "Second"
                                                                   :fi "Toinen"}}
                                                          {:key   "Option3"
                                                           :label {:en "Third"
                                                                   :fi "Kolmas "}}]
                                         :field/optional true
                                         :field/visible  true
                                         :field/value    "Option1"}]))))

  (testing "error: field input option can be left empty when optional"
    (is (= nil
           (validate-fields-for-submit [{:field/id       "1"
                                         :field/title    {:en "Option list."
                                                          :fi "Valintalista."}
                                         :field/type     :option
                                         :field/options  [{:key   "Option1"
                                                           :label {:en "First"
                                                                   :fi "Ensimmäinen"}}
                                                          {:key   "Option2"
                                                           :label {:en "Second"
                                                                   :fi "Toinen"}}
                                                          {:key   "Option3"
                                                           :label {:en "Third"
                                                                   :fi "Kolmas "}}]
                                         :field/optional true
                                         :field/visible  true
                                         :field/value    ""}]))))

  (testing "error: field input invalid email address"
    (is (= [{:type :t.form.validation/required :field-id "1"}
            {:type :t.form.validation/invalid-email :field-id "2"}
            {:type :t.form.validation/invalid-email :field-id "5"}]
           (validate-fields-for-submit [{:field/id       "1"
                                         :field/type     :email
                                         :field/optional false
                                         :field/visible  true
                                         :field/value    ""}
                                        {:field/id       "2"
                                         :field/type     :email
                                         :field/optional false
                                         :field/visible  true
                                         :field/value    "invalid.email"}
                                        {:field/id       "3"
                                         :field/type     :email
                                         :field/optional false
                                         :field/visible  true
                                         :field/value    "valid.email@example.com"}
                                        {:field/id       "4"
                                         :field/type     :email
                                         :field/optional true
                                         :field/visible  true
                                         :field/value    ""}
                                        {:field/id       "5"
                                         :field/type     :email
                                         :field/optional true
                                         :field/visible  true
                                         :field/value    "invalid.email"}])))))

(deftest test-validate-fields-for-draft
  (testing "required fields do not need to be filled at draft stage"
    (is (nil? (validate-fields-for-draft [{:field/title    "A"
                                           :field/optional false
                                           :field/visible  true
                                           :field/value    nil}
                                          {:field/title    "B"
                                           :field/optional false
                                           :field/visible  true
                                           :field/value    ""}]))))


  (testing "optional fields do not need to be filled"
    (is (nil? (validate-fields-for-draft [{:field/title    "A"
                                           :field/optional true
                                           :field/visible  true
                                           :field/value    nil}
                                          {:field/title    "B"
                                           :field/optional true
                                           :field/visible  true
                                           :field/value    ""}]))))

  (testing "invisible fields do not need to be filled"
    (is (nil? (validate-fields-for-draft [{:field/title    "A"
                                           :field/optional true
                                           :field/visible  false
                                           :field/value    nil}
                                          {:field/title    "B"
                                           :field/optional true
                                           :field/visible  false
                                           :field/value    ""}]))))

  (testing "labels and headers are always effectively optional"
    (is (nil? (validate-fields-for-draft [{:field/type     :label
                                           :field/optional false
                                           :field/visible  true
                                           :field/value    ""}
                                          {:field/type     :label
                                           :field/optional true
                                           :field/visible  true
                                           :field/value    ""}
                                          {:field/type     :header
                                           :field/optional false
                                           :field/visible  true
                                           :field/value    ""}
                                          {:field/type     :header
                                           :field/optional true
                                           :field/visible  true
                                           :field/value    ""}]))))

  (testing "error: field input too long"
    (is (= [{:type :t.form.validation/toolong :field-id "2"}]
           (validate-fields-for-draft [{:field/id         "1"
                                        :field/max-length 5
                                        :field/visible    true
                                        :field/value      "abcde"}
                                       {:field/id         "2"
                                        :field/max-length 5
                                        :field/visible    true
                                        :field/value      "abcdef"}]))))

  (testing "attachment field"
    (let [field {:field/id         "fld1"
                 :field/type       :attachment
                 :field/visible    true}]
      (is (= [{:type :t.form.validation/invalid-value :field-id "fld1"}]
             (validate-fields-for-draft [(assoc field :field/value "1;2")])))
      (is (= [{:type :t.form.validation/invalid-value :field-id "fld1"}]
             (validate-fields-for-draft [(assoc field :field/value "1 2")])))
      (is (= [{:type :t.form.validation/invalid-value :field-id "fld1"}]
             (validate-fields-for-draft [(assoc field :field/value "1,a,2")])))
      (is (nil? (validate-fields-for-draft [(assoc field :field/value "1")])))
      (is (nil? (validate-fields-for-draft [(assoc field :field/value "1,2,3")])))))

  (testing "string required for"
    (doseq [type [:text :texta :date :email :attachment :description]]
      (testing type
        (is (= [{:type :t.form.validation/invalid-value :field-id "fld1"}]
               (validate-fields-for-draft [{:field/id "fld1" :field/visible true :field/type type :field/value 1}])))
        (is (= [{:type :t.form.validation/invalid-value :field-id "fld1"}]
               (validate-fields-for-draft [{:field/id "fld1" :field/visible true :field/type type :field/value {:foo "bar"}}])))
        (is (= [{:type :t.form.validation/invalid-value :field-id "fld1"}]
               (validate-fields-for-draft [{:field/id "fld1" :field/visible true :field/type type :field/value [{:foo "bar"}]}]))))))

  (testing "error: field input selected option is invalid"
    (is (= [{:type :t.form.validation/invalid-value :field-id "1"}]
           (validate-fields-for-draft [{:field/id       "1"
                                        :field/title    {:en "Option list."
                                                         :fi "Valintalista."}
                                        :field/type     :option
                                        :field/options  [{:key   "Option1"
                                                          :label {:en "First"
                                                                  :fi "Ensimmäinen"}}
                                                         {:key   "Option2"
                                                          :label {:en "Second"
                                                                  :fi "Toinen"}}
                                                         {:key   "Option3"
                                                          :label {:en "Third"
                                                                  :fi "Kolmas "}}]
                                        :field/optional true
                                        :field/visible  true
                                        :field/value    "foobar"}
                                       {:field/id       "2"
                                        :field/title    {:en "Option list."
                                                         :fi "Valintalista."}
                                        :field/type     :option
                                        :field/options  [{:key   "Option1"
                                                          :label {:en "First"
                                                                  :fi "Ensimmäinen"}}
                                                         {:key   "Option2"
                                                          :label {:en "Second"
                                                                  :fi "Toinen"}}
                                                         {:key   "Option3"
                                                          :label {:en "Third"
                                                                  :fi "Kolmas "}}]
                                        :field/optional true
                                        :field/visible  true
                                        :field/value    "Option1"}]))))

  (testing "error: field input option can be left empty when optional"
    (is (= nil
           (validate-fields-for-draft [{:field/id       "1"
                                        :field/title    {:en "Option list."
                                                         :fi "Valintalista."}
                                        :field/type     :option
                                        :field/options  [{:key   "Option1"
                                                          :label {:en "First"
                                                                  :fi "Ensimmäinen"}}
                                                         {:key   "Option2"
                                                          :label {:en "Second"
                                                                  :fi "Toinen"}}
                                                         {:key   "Option3"
                                                          :label {:en "Third"
                                                                  :fi "Kolmas "}}]
                                        :field/optional true
                                        :field/visible  true
                                        :field/value    ""}]))))

  (testing "error: field input invalid email address"
    (is (= [{:type :t.form.validation/invalid-email :field-id "2"}
            {:type :t.form.validation/invalid-email :field-id "5"}]
           (validate-fields-for-draft [{:field/id       "1"
                                        :field/type     :email
                                        :field/optional false
                                        :field/visible  true
                                        :field/value    ""}
                                       {:field/id       "2"
                                        :field/type     :email
                                        :field/optional false
                                        :field/visible  true
                                        :field/value    "invalid.email"}
                                       {:field/id       "3"
                                        :field/type     :email
                                        :field/optional false
                                        :field/visible  true
                                        :field/value    "valid.email@example.com"}
                                       {:field/id       "4"
                                        :field/type     :email
                                        :field/optional true
                                        :field/visible  true
                                        :field/value    ""}
                                       {:field/id       "5"
                                        :field/type     :email
                                        :field/optional true
                                        :field/visible  true
                                        :field/value    "invalid.email"}])))))
