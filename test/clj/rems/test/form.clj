(ns rems.test.form
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.form :as form]
            [rems.test.tempura :refer [with-fake-tempura]]
            [ring.mock.request :refer :all])
  (:import rems.InvalidRequestException))

(def validate #'form/validate)

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
