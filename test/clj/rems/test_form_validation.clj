(ns rems.test-form-validation
  (:refer-clojure :exclude [validate-fields])
  (:require [clojure.test :refer :all]
            [rems.form-validation :refer [validate-fields]]))

(deftest test-validate-fields
  (testing "all fields filled"
    (is (nil? (validate-fields [{:field/title "A"
                                 :field/optional false
                                 :field/value "a"}]))))


  (testing "optional fields do not need to be filled"
    (is (nil? (validate-fields [{:field/title "A"
                                 :field/optional true
                                 :field/value nil}
                                {:field/title "B"
                                 :field/optional true
                                 :field/value ""}]))))

  (testing "labels are always effectively optional"
    (is (nil? (validate-fields [{:field/type :label
                                 :field/optional false
                                 :field/value ""}
                                {:field/type :label
                                 :field/optional true
                                 :field/value ""}]))))

  (testing "error: field required"
    (is (= [{:type :t.form.validation/required :field-id 2}
            {:type :t.form.validation/required :field-id 3}
            {:type :t.form.validation/required :field-id 4}]
           (validate-fields [{:field/id 1
                              :field/optional true
                              :field/value nil}
                             {:field/id 2
                              :field/optional false
                              :field/value nil}
                             {:field/id 3
                              :field/optional false
                              :field/value ""}
                             {:field/id 4
                              :field/optional false
                              :field/value "    "}]))))

  (testing "error: field input too long"
    (is (= [{:type :t.form.validation/toolong :field-id 2}]
           (validate-fields [{:field/id 1
                              :field/max-length 5
                              :field/value "abcde"}
                             {:field/id 2
                              :field/max-length 5
                              :field/value "abcdef"}])))))
