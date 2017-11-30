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

(def field #'form/field)

(deftest test-license-field
  (let [f (field {:type "license" :licensetype "link" :textcontent "ab.c" :title "Link to license"})
        [[_ attrs]] (hiccup-find [:input] f)
        [[_ target]] (hiccup-find [:a] f)]
    (is (= "checkbox" (:type attrs))
        "Checkbox exists for supported license type")
    (is (= "_blank" (:target target))
        "License with type link opens to a separate tab"))
  (let [f (field {:type "license" :licensetype "attachment" :textcontent "ab.c" :title "Link to license"})]
    (is (.contains (hiccup-text f) "Unsupported field ")
        "Unsupported license type gives a warning")))

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

(deftest test-save
  (with-fake-tempura
    ;; simple mock db
    (let [world (atom {:submitted #{}})
          catalogue-items (atom [{:id 1}])
          run (fn [path params]
                (form/form-routes
                 (assoc (request :post path)
                        :form-params params)))]
      (with-redefs
        [rems.db.applications/get-form-for
         (fn [application]
           {:id 137
            :application (get-in @world [:applications application])
            :state (get-in @world [:states application])
            :items [{:id 61
                     :title "A"
                     :type "text"
                     :optional true
                     :value (get-in @world [:values application 61])}
                    {:id 62
                     :title "B"
                     :type "text"
                     :optional false
                     :value (get-in @world [:values application 62])}]
            :catalogue-items @catalogue-items
            :licenses [{:id 70
                        :type "license"
                        :licensetype "link"
                        :title "KielipankkiTerms"
                        :textcontent "https://kitwiki.csc.fi/twiki/bin/view/FinCLARIN/KielipankkiTerms"
                        :approved (get-in @world [:approvals application 70])}]})

         rems.db.applications/get-application-state
         (fn [application]
           {:id application :applicantuserid "alice" :start nil :wfid 1 :fnlround 0 :state (get-in @world [:states application])
            :curround 0 :events ()})

         db/save-field-value!
         (fn [{application :application
               item :item
               value :value}]
           (swap! world assoc-in [:values application item] value))

         db/save-license-approval!
         (fn [{application :catappid
               licid :licid
               state :state}]
           (swap! world assoc-in [:approvals application licid] state))

         db/delete-license-approval!
         (fn [{application :catappid
               licid :licid}]
           (swap! world dissoc :approvals))

         db/create-application!
         (constantly {:id 2})

         db/add-application-item!
         (fn [params])

         applications/submit-application
         (fn [application-id]
           (swap! world update :submitted conj application-id))]

        (testing "first save"
          (let [resp (run "/form/2/save" {"field61" "x"
                                          "field62" "y"
                                          "license70" "approved"})
                flash (first (:flash resp))
                flash-text (hiccup-text (:contents flash))]
            (is (= 303 (:status resp)))
            (testing flash
              (is (= :success (:status flash)))
              (is (.contains flash-text "saved")))
            (is (= {:submitted #{} :values {2 {61 "x", 62 "y"}} :approvals {2 {70 "approved"}}}
                   @world))))

        (testing "second save, with missing optional field. shouldn't create new draft"
          (let [resp (run "/form/2/save" {"field61" ""
                                          "field62" "z"
                                          "license70" "approved"})
                flash (first (:flash resp))
                flash-text (hiccup-text (:contents flash))]
            (is (= 303 (:status resp)))
            (testing flash
              (is (= :success (:status flash)))
              (is (.contains flash-text "saved")))
            (is (= {:submitted #{} :values {2 {61 "", 62 "z"}} :approvals {2 {70 "approved"}}}
                   @world))))

        (testing "save with unchecked license"
          (let [resp (run "/form/2/save" {"field61" "x"
                                          "field62" "y"})
                [flash1 flash2] (:flash resp)
                flash1-text (hiccup-text (:contents flash1))
                flash2-text (hiccup-text (:contents flash2))]
            (is (= 303 (:status resp)))
            (testing flash1
              (is (= :success (:status flash1)))
              (is (.contains flash1-text "saved")))
            (testing flash2
              (is (= :info (:status flash2)))
              (is (.contains flash2-text "\"KielipankkiTerms\"")))
            (is (= {:submitted #{} :values {2 {61 "x", 62 "y"}}}
                   @world))))

        (testing "save with missing mandatory field"
          (let [resp (run "/form/2/save" {"field61" "w"
                                          "field62" ""
                                          "license70" "approved"})
                [flash1 flash2] (:flash resp)
                flash1-text (hiccup-text (:contents flash1))
                flash2-text (hiccup-text (:contents flash2))]
            (testing flash1
              (is (= :success (:status flash1)))
              (is (.contains flash1-text "saved")))
            (testing flash2
              (is (= :info (:status flash2)))
              (is (.contains flash2-text "\"B\"")))
            (is (= {:submitted #{} :values {2 {61 "w", 62 ""}} :approvals {2 {70 "approved"}}}
                   @world))))

        (testing "submit with missing mandatory field"
          (let [resp (run "/form/2/save" {"field61" "u"
                                          "field62" ""
                                          "license70" "approved"
                                          "submit" "true"})
                [flash1 flash2] (:flash resp)
                flash1-text (hiccup-text (:contents flash1))
                flash2-text (hiccup-text (:contents flash2))]
            (testing flash1
              (is (= :warning (:status flash1)))
              (is (.contains flash1-text "saved"))
              (is (not (.contains flash1-text "submitted"))))
            (testing flash2
              (is (= :warning (:status flash2)))
              (is (.contains flash2-text "\"B\"")))
            (is (= {:submitted #{} :values {2 {61 "u", 62 ""}} :approvals {2 {70 "approved"}}}
                   @world))))

        (testing "submit with unchecked license"
          (let [resp (run "/form/2/save" {"field61" ""
                                          "field62" "v"
                                          "submit" "true"})
                [flash1 flash2] (:flash resp)
                flash1-text (hiccup-text (:contents flash1))
                flash2-text (hiccup-text (:contents flash2))]
            (testing flash1
              (is (= :warning (:status flash1)))
              (is (.contains flash1-text "saved"))
              (is (not (.contains flash1-text "submitted"))))
            (testing flash2
              (is (= :warning (:status flash2)))
              (is (.contains flash2-text "\"KielipankkiTerms\"")))
            (is (= {:submitted #{} :values {2 {61 "", 62 "v"}}}
                   @world))))

        (testing "successful submit"
          (let [resp (run "/form/2/save" {"field61" ""
                                          "field62" "v"
                                          "license70" "approved"
                                          "submit" "true"})
                flash (first (:flash resp))
                flash-text (hiccup-text (:contents flash))]
            (testing flash
              (is (= :success (:status flash)))
              (is (not (.contains flash-text "saved")))
              (is (.contains flash-text "submitted")))
            (is (= {:submitted #{2} :values {2 {61 "", 62 "v"}} :approvals {2 {70 "approved"}}}
                   @world))))

        (testing "submit with disabled item"
          (swap! catalogue-items conj {:state "disabled"})
          (is (thrown? InvalidRequestException
                       (run "/form/2/save"
                         {"field61" ""
                          "field62" "v"
                          "license70" "approved"
                          "submit" "true"}))))))))

(def form #'form/form)

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

(deftest test-events
  (with-fake-tempura
    (let [get-comments #(mapv hiccup-string
                              (hiccup-find [:#event-table :.event-comment] %))
          data {:application
                {:id 17
                 :can-approve? false
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

(defn- get-actions [form-data]
  ;; TODO could look at button actions too
  (->> (form form-data)
       (hiccup-find [:.btn])
       (map hiccup-attrs)
       (keep :id)
       (set)))

(deftest test-form-actions
  (with-fake-tempura
    (let [draft-data {:application {:id 2
                                    :applicantuserid "developer"
                                    :state "draft"
                                    :curround 0
                                    :fnlround 1
                                    :wfid 2
                                    :can-approve? false
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
              (is (= #{"save" "back-catalogue" "submit" "close"} (get-actions draft-data))))
            (testing "on an applied form"
              (is (= #{"withdraw" "close" "back-catalogue"} (get-actions applied-data))))
            (testing "on an approved form"
              (is (= #{"close" "back-catalogue"} (get-actions approved-data))))))
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
