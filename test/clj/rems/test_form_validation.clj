(ns rems.test-form-validation
  (:require [clojure.test :refer :all]
            [rems.form-validation :refer [validate]]))

(deftest test-validate
  (is (= :valid (validate
                 {:items [{:title "A"
                           :optional true
                           :value nil}
                          {:title "B"
                           :optional false
                           :value "xyz"}
                          {:title "C"
                           :optional false
                           :value "1"}]})))

  (is (= [{:type :t.form.validation/required :license-id 123}]
         (validate
          {:items [{:title "A"
                    :optional false
                    :value "a"}]
           :licenses [{:id 123
                       :title "LGPL"}]})))

  (is (= :valid (validate
                 {:items [{:title "A"
                           :optional false
                           :value "a"}]
                  :licenses [{:title "LGPL"
                              :approved true}]})))

  (is (= [{:type :t.form.validation/required :field-id 2}
          {:type :t.form.validation/required :field-id 3}]
         (validate
          {:items [{:id 1
                    :localizations {:en {:title "A"}}
                    :optional true
                    :value nil}
                   {:id 2
                    :localizations {:en {:title "B"}}
                    :optional false
                    :value ""}
                   {:id 3
                    :localizations {:en {:title "C"}}
                    :optional false
                    :value nil}]}))))
