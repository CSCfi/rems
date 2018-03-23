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

#_
(deftest test-form-actions
  (with-fake-tempura
    (let [draft-data {:application {:id 2
                                    :applicantuserid "developer"
                                    :state "draft"
                                    :curround 0
                                    :fnlround 1
                                    :wfid 2
                                    :can-approve? false
                                    :can-close? false
                                    :review-type nil
                                    :events []}}
          applied-data {:application {:id 2
                                      :applicantuserid "developer"
                                      :start nil
                                      :wfid 2
                                      :fnlround 1
                                      :state "applied"
                                      :curround 0
                                      :can-approve? false
                                      :can-close? false
                                      :review-type nil
                                      :events
                                      [{:userid "developer"
                                        :round 0
                                        :event "apply"
                                        :comment nil
                                        :time nil}
                                       {:userid "lenny"
                                        :round 0
                                        :event "review-request"
                                        :comment nil
                                        :time nil}]}}
          approved-data {:application {:id 2
                                       :applicantuserid "developer"
                                       :start nil
                                       :wfid 2
                                       :fnlround 0
                                       :state "approved"
                                       :curround 0
                                       :can-approve? false
                                       :can-close? false
                                       :review-type nil
                                       :events
                                       [{:userid "developer"
                                         :round 0
                                         :event "apply"
                                         :comment nil
                                         :time nil}
                                        {:userid "lenny"
                                         :round 0
                                         :event "review-request"
                                         :comment nil
                                         :time nil}
                                        {:userid "bob"
                                         :round 0
                                         :event "approved"
                                         :comment nil
                                         :time nil}]}}]
      (with-redefs [rems.db.core/get-users (fn [] [])
                    rems.db.core/get-user-attributes (fn [_] nil)]
        ;; TODO these cases can be simplified now that we have :can-approve?
        ;; and :review-type
        (binding [context/*user* {"eppn" "developer"}
                  context/*roles* #{:applicant}]
          (testing "As the applicant"
            (testing "on a new form"
              (is (= #{"save" "back-catalogue" "submit"}
                     (get-actions
                      (assoc-in draft-data [:application :id] -6)))))
            (testing "on a draft"
              (is (= #{"save" "back-catalogue" "submit"} (get-actions draft-data))))
            (testing "on an applied form"
              (is (= #{"withdraw" "close" "back-catalogue"}
                     (get-actions (assoc-in applied-data [:application :can-close?] true)))))
            (testing "on an approved form"
              (is (= #{"back-catalogue"} (get-actions approved-data))))))
        (binding [context/*user* {"eppn" "bob"}
                  context/*roles* #{:approver :applicant}]
          (testing "As a current round approver (who is also an applicant)"
            (testing "on an applied form"
              (is (= #{"back-actions" "reject" "approve" "review-request" "return"}
                     (get-actions
                      (assoc-in applied-data [:application :can-approve?] true))))))
          (testing "As an approver (who is also an applicant)"
            (testing "on an approved form"
              (is (= #{"back-actions"} (get-actions approved-data))))))
        (testing "As an approver, who is not set for the current round, on an applied form"
          (binding [context/*user* {"eppn" "carl"}
                    context/*roles* #{:approver}]
            (is (= #{"back-actions"} (get-actions applied-data)))))
        (testing "As a reviewer"
          (binding [context/*user* {"eppn" "carl"}
                    context/*roles* #{:reviewer}]
            (testing "on an applied form"
              (is (= #{"back-actions" "review"}
                     (get-actions
                      (assoc-in applied-data [:application :review-type] :normal)))))
            (testing "on an approved form"
              (is (= #{"back-actions"} (get-actions approved-data))))))
        (testing "As a reviewer, who is not set for the current round, on an applied form"
          (binding [context/*user* {"eppn" "bob"}
                    context/*roles* #{:reviewer}]
            (is (= #{"back-actions"} (get-actions applied-data)))))
        (testing "As a 3d party reviewer"
          (binding [context/*user* {"eppn" "lenny"}
                    context/*roles* #{:reviewer}]
            (testing "on an applied form"
              (is (= #{"back-actions" "third-party-review"}
                     (get-actions
                      (assoc-in applied-data [:application :review-type] :third-party)))))
            (testing "on an approved form"
              (is (= #{"back-actions"} (get-actions approved-data))))))))))
