(ns rems.test-form-validation
  (:require [clojure.test :refer [deftest testing is]]
            [rems.form-validation :refer [validate-fields]]))

(deftest test-validate-fields
  (testing "all fields filled"
    (is (nil? (validate-fields [{:field/title    "A"
                                 :field/optional false
                                 :field/visible  true
                                 :field/value    "a"}]))))

  (testing "optional fields do not need to be filled"
    (is (nil? (validate-fields [{:field/title    "A"
                                 :field/optional true
                                 :field/visible  true
                                 :field/value    nil}
                                {:field/title    "B"
                                 :field/optional true
                                 :field/visible  true
                                 :field/value    ""}]))))

  (testing "invisible fields do not need to be filled"
    (is (nil? (validate-fields [{:field/title    "A"
                                 :field/optional true
                                 :field/visible  false
                                 :field/value    nil}
                                {:field/title    "B"
                                 :field/optional true
                                 :field/visible  false
                                 :field/value    ""}
                                {:field/title    "C"
                                 :field/optional false
                                 :field/visible  false
                                 :field/value    ""}]))))

  (testing "labels and headers are always effectively optional"
    (is (nil? (validate-fields [{:field/type     :label
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
           (validate-fields [{:field/id       "1"
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
           (validate-fields [{:field/id         "1"
                              :field/max-length 5
                              :field/visible    true
                              :field/value      "abcde"}
                             {:field/id         "2"
                              :field/max-length 5
                              :field/visible    true
                              :field/value      "abcdef"}]))))

  (testing "error: field input selected option is invalid"
    (is (= [{:type :t.form.validation/invalid-value :field-id "1"}]
           (validate-fields [{:field/id       "1"
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
           (validate-fields [{:field/id       "1"
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

  (testing "error: multiselect validation"
    (is (= nil
           (validate-fields [{:field/id       "1"
                              :field/type     :multiselect
                              :field/visible  true
                              :field/optional true
                              :field/options  [{:key   "ye"}
                                               {:key   "no"}
                                               {:key   "xx"}]
                              :field/value    ""}])
           (validate-fields [{:field/id       "1"
                              :field/type     :multiselect
                              :field/visible  true
                              :field/optional true
                              :field/options  [{:key   "ye"}
                                               {:key   "no"}
                                               {:key   "xx"}]
                              :field/value    "ye"}])
           (validate-fields [{:field/id       "1"
                              :field/type     :multiselect
                              :field/visible  true
                              :field/optional true
                              :field/options  [{:key   "ye"}
                                               {:key   "no"}
                                               {:key   "xx"}]
                              :field/value    "no"}])
           (validate-fields [{:field/id       "1"
                              :field/type     :multiselect
                              :field/visible  true
                              :field/optional true
                              :field/options  [{:key   "ye"}
                                               {:key   "no"}
                                               {:key   "xx"}]
                              :field/value    "ye no xx"}])))
    (is (= [{:type :t.form.validation/invalid-value :field-id "1"}]
           (validate-fields [{:field/id       "1"
                              :field/type     :multiselect
                              :field/visible  true
                              :field/optional true
                              :field/options  [{:key   "ye"}
                                               {:key   "no"}
                                               {:key   "xx"}]
                              :field/value    "foo"}])
           (validate-fields [{:field/id       "1"
                              :field/type     :multiselect
                              :field/visible  true
                              :field/optional true
                              :field/options  [{:key   "ye"}
                                               {:key   "no"}
                                               {:key   "xx"}]
                              :field/value    "ye foo xx"}])))
    (is (= [{:type :t.form.validation/required :field-id "1"}]
           (validate-fields [{:field/id       "1"
                              :field/type     :multiselect
                              :field/visible  true
                              :field/optional false
                              :field/options  [{:key   "ye"}
                                               {:key   "no"}
                                               {:key   "xx"}]
                              :field/value    ""}]))))

  (testing "error: field input invalid email address"
    (is (= [{:type :t.form.validation/required :field-id "1"}
            {:type :t.form.validation/invalid-email :field-id "2"}
            {:type :t.form.validation/invalid-email :field-id "5"}]
           (validate-fields [{:field/id       "1"
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
                              :field/value    "invalid.email"}]))))

  (testing "table validation"
    (let [fields [{:field/id "tbl"
                   :field/type :table
                   :field/visible true
                   :field/optional true
                   :field/columns [{:key "col1"} {:key "col2"}]}]]
      (testing "optional"
        (is (nil? (validate-fields (assoc-in fields [0 :field/value] []))))
        (is (nil? (validate-fields (assoc-in fields [0 :field/value] [[{:column "col1" :value "1"}
                                                                       {:column "col2" :value "2"}]
                                                                      [{:column "col1" :value "1"}
                                                                       {:column "col2" :value "2"}]])))))
      (testing "required"
        (let [required (assoc-in fields [0 :field/optional] false)]
          (is (= [{:field-id "tbl", :type :t.form.validation/required}]
                 (validate-fields (assoc-in required [0 :field/value] ""))))
          (is (= [{:field-id "tbl", :type :t.form.validation/required}]
                 (validate-fields (assoc-in required [0 :field/value] []))))
          (is (nil? (validate-fields (assoc-in required [0 :field/value] [[{:column "col1" :value "1"}
                                                                           {:column "col2" :value "2"}]]))))))))

  (testing "error: field input invalid ip address"
    (is (= [{:type :t.form.validation/required :field-id "empty ip address"}
            {:type :t.form.validation/invalid-ip-address :field-id "invalid ip address"}
            {:type :t.form.validation/invalid-ip-address-private :field-id "private ip address"}]
           (validate-fields [{:field/id       "empty ip address"
                              :field/type     :ip-address
                              :field/optional false
                              :field/visible  true
                              :field/value    ""}
                             {:field/id       "invalid ip address"
                              :field/type     :ip-address
                              :field/optional false
                              :field/visible  true
                              :field/value    "+058450000100"}
                             {:field/id       "correct ip address"
                              :field/type     :ip-address
                              :field/optional false
                              :field/visible  true
                              :field/value    "142.250.74.110"}
                             {:field/id       "private ip address"
                              :field/type     :ip-address
                              :field/optional true
                              :field/visible  true
                              :field/value    "192.0.2.255"}]))))

  (testing "attachment field"
    (let [field {:field/id "fld1"
                 :field/type :attachment
                 :field/visible true}]
      (is (= [{:type :t.form.validation/invalid-value :field-id "fld1"}]
             (validate-fields [(assoc field :field/value "1;2")])))
      (is (= [{:type :t.form.validation/invalid-value :field-id "fld1"}]
             (validate-fields [(assoc field :field/value "1 2")])))
      (is (= [{:type :t.form.validation/invalid-value :field-id "fld1"}]
             (validate-fields [(assoc field :field/value "1,a,2")])))
      (is (nil? (validate-fields [(assoc field :field/value "1")])))
      (is (nil? (validate-fields [(assoc field :field/value "1,2,3")])))))

  (testing "string required for"
    (doseq [type [:text :texta :date :email :attachment :description]]
      (testing type
        (is (= [{:type :t.form.validation/invalid-value :field-id "fld1"}]
               (validate-fields [{:field/id "fld1" :field/visible true :field/type type :field/value 1}])))
        (is (= [{:type :t.form.validation/invalid-value :field-id "fld1"}]
               (validate-fields [{:field/id "fld1" :field/visible true :field/type type :field/value {:foo "bar"}}])))
        (is (= [{:type :t.form.validation/invalid-value :field-id "fld1"}]
               (validate-fields [{:field/id "fld1" :field/visible true :field/type type :field/value [{:foo "bar"}]}]))))))

  (testing "error: field input invalid phone number"
    (is (= [{:type :t.form.validation/required :field-id "empty phone number"}
            {:type :t.form.validation/invalid-phone-number :field-id "less than 5 symbols is not allowed"}
            {:type :t.form.validation/invalid-phone-number :field-id "more than 25 symbols is not allowed"}
            {:type :t.form.validation/invalid-phone-number :field-id "number has to start with a + prefix"}
            {:type :t.form.validation/invalid-phone-number :field-id "prefix 0 is not allowed"}]
           (validate-fields [{:field/id       "empty phone number"
                              :field/type     :phone-number
                              :field/optional false
                              :field/visible  true
                              :field/value    ""}
                             {:field/id       "prefix 0 is not allowed"
                              :field/type     :phone-number
                              :field/optional false
                              :field/visible  true
                              :field/value    "+058450000100"}
                             {:field/id       "more than 25 symbols is not allowed"
                              :field/type     :phone-number
                              :field/optional false
                              :field/visible  true
                              :field/value    "+15005000010000000000000000000000"}
                             {:field/id       "less than 5 symbols is not allowed"
                              :field/type     :phone-number
                              :field/optional true
                              :field/visible  true
                              :field/value    "+3500"}
                             {:field/id       "number has to start with a + prefix"
                              :field/type     :phone-number
                              :field/optional true
                              :field/visible  true
                              :field/value    "35000000000000"}
                             {:field/id       "valid phone number"
                              :field/type     :phone-number
                              :field/optional true
                              :field/visible  true
                              :field/value    "+358450000100"}])))))

