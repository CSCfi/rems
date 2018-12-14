(ns rems.test.form-validation
  (:require [clojure.test :refer :all]
            [rems.form-validation :refer [validate]]
            [rems.test.tempura :refer [with-fake-tempura]]))

(deftest test-validate
  (with-fake-tempura
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

    (let [res (validate
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
                         :value nil}]})]
      (testing res
        (is (vector? res))
        (is (= 2 (count res)))
        (is (.contains (:text (first res)) "B"))
        (is (.contains (:text (second res)) "C"))))))
