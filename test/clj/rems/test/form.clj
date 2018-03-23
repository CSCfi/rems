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

#_
(deftest test-events
  (with-fake-tempura
    (let [get-comments #(mapv hiccup-string
                              (hiccup-find [:#event-table :.event-comment] %))
          data {:application
                {:id 17
                 :can-approve? false
                 :can-close? false
                 :review-type nil
                 :events [{:event "apply" :comment "APPLY"}
                          {:event "withdraw" :comment "WITHDRAW"}
                          {:event "autoapprove" :comment "AUTO"}
                          {:event "review-request" :comment "REQUEST"}
                          {:event "third-party-review" :comment "THIRD"}
                          {:event "review" :comment "REVIEW"}
                          {:event "approve" :comment "APPROVE"}
                          {:event "close" :comment "CLOSE"}]}}]
      (doseq [role [:approver :reviewer]]
        (testing role
          (testing "sees all comments"
            (binding [context/*roles* #{role}
                      context/*user* {"eppn" "bob"}]
              (let [body (form data)
                    comments (get-comments body)]
                (is (= (map :comment (get-in data [:application :events]))
                       comments)))))))
      (testing "applicant"
        (binding [context/*roles* #{:applicant}
                  context/*user* {"eppn" "bob"}]
          (let [body (form data)
                comments (get-comments body)]
            (testing "doesn't see review events"
              (= ["APPLY" "WITHDRAW" "AUTO" "APPROVE" "CLOSE"]
                 comments))))))))

#_
(deftest test-applicant-info
  (with-fake-tempura
    (let [get-info #(hiccup-find [:#applicant-info] %)
          data {:application {:id 66
                              :can-approve? false
                              :can-close? false
                              :state "draft"
                              :review-type nil
                              :applicantuserid "bob"
                              :events []}}]
      (testing "applicant doesn't see applicant details"
        (binding [context/*user* {"eppn" "bob"}
                  context/*roles* #{:applicant :approver}]
          (is (empty? (get-info (form data))))))
      (testing "others see applicant details"
        (binding [context/*user* {"eppn" "jeff"}
                  context/*roles* #{:applicant :approver}]
          (is (not (empty? (get-info (form data))))))))))

#_
(defn- get-actions [form-data]
  ;; TODO could look at button actions too
  (->> (form form-data)
       (hiccup-find [:.btn])
       (map hiccup-attrs)
       (keep :id)
       (set)))
