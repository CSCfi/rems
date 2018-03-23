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
               {:items [{:title "A"
                         :optional true
                         :value nil}
                        {:title "B"
                         :optional false
                         :value ""}
                        {:title "C"
                         :optional false
                         :value nil}]})]
      (testing res
        (is (vector? res))
        (is (= 2 (count res)))
        (is (.contains (first res) "B"))
        (is (.contains (second res) "C"))))))

#_
(deftest test-editable
  (with-fake-tempura
    (binding [context/*roles* #{:applicant}]
      (let [readonly? (fn [[_tag attrs]]
                        (case (:type attrs)
                          "checkbox" (:disabled attrs) ;; checkboxes are special
                          (:readonly attrs)))
            all-inputs (fn [body] (remove #(= "comment" (:name (second %)))
                                          (concat (hiccup-find [:div.form-group.field :input] body)
                                                  (hiccup-find [:div.form-group.field :textarea] body))))
            submit-button #(first (hiccup-find [:.submit-button] %))
            data {:application {:can-approve? false
                                :can-close? false
                                :review-type nil}
                  :items [{:type "text"}
                          {:type "texta"}]
                  :licenses [{:type "license" :licensetype "link"
                              :textcontent "" :title ""}]}]
        (testing "new form"
          (let [body (form data)]
            (is (= [false false false] (map readonly? (all-inputs body))))
            (is (submit-button body))))
        (testing "draft"
          (let [body (form (assoc-in data [:application :state] "draft"))]
            (is (= [false false false] (map readonly? (all-inputs body))))
            (is (submit-button body))))
        (doseq [state ["applied" "approved" "rejected"]]
          (testing state
            (let [body (form (-> data
                                 (assoc-in [:application :id] 1)
                                 (assoc-in [:application :state] state)))]
              (is (= [true true true] (map readonly? (all-inputs body))))
              (is (nil? (submit-button body))))))))))
