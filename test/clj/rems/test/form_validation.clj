(ns rems.test.form-validation
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

  (is (not= :valid (validate
                    {:items [{:title "A"
                              :optional false
                              :value "a"}]
                     :licenses [{:title "LGPL"}]})))

  (is (= :valid (validate
                 {:items [{:title "A"
                           :optional false
                           :value "a"}]
                  :licenses [{:title "LGPL"
                              :approved true}]})))

  (is (= [{:type :item, :id 2, :key :t.form.validation/required}
          {:type :item, :id 3, :key :t.form.validation/required}]
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
