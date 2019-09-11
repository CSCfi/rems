(ns rems.poller.test-email
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.application.model :as model]
            [rems.config]
            [rems.db.user-settings :as user-settings]
            [rems.locales]
            [rems.poller.email :refer :all]
            [rems.text :as text]))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'rems.config/env
                 #'rems.locales/translations)
    (f)
    (mount/stop)))

(deftest test-send-email!
  ;; Just for a bit of coverage in code that doesn't get run in other tests or the dev profile
  (let [message-atom (atom nil)]
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :smtp-host "localhost"
                                         :smtp-port 25
                                         :mail-from "rems@rems.rems")
                  postal.core/send-message (fn [_host message] (reset! message-atom message))
                  rems.db.users/get-user-attributes (constantly {:mail "user@example.com"})]
      (send-email! {:to "foo@example.com" :subject "ding" :body "boing"})
      (is (= {:to "foo@example.com"
              :subject "ding"
              :body "boing"
              :from "rems@rems.rems"}
             @message-atom))
      (send-email! {:to-user "user" :subject "ding" :body "boing"})
      (is (= {:to "user@example.com"
              :to-user "user"
              :subject "ding"
              :body "boing"
              :from "rems@rems.rems"}
             @message-atom)))))

(defn sort-emails [emails]
  (sort-by #(or (:to %) (:to-user %)) emails))

(def ^:private get-catalogue-item
  {10 {:localizations {:en {:langcode :en
                            :title "en title 11"}
                       :fi {:langcode :fi
                            :title "fi title 11"}}}
   20 {:localizations {:en {:langcode :en
                            :title "en title 21"}
                       :fi {:langcode :fi
                            :title "fi title 21"}}}})

(def ^:private get-workflow
  {5 {:workflow {:handlers [{:userid "handler"
                             :name "Handler"
                             :email "handler@example.com"}
                            {:userid "assistant"
                             :name "Assistant"
                             :email "assistant@example.com"}]}}})

(def ^:private get-form-template
  (constantly {:form/id 40
               :form/fields [{:field/id 1
                              :field/title {:en "en title" :fi "fi title"}
                              :field/optional false
                              :field/type :description}]}))

(def ^:private get-license
  (constantly {:id 1234
               :licensetype "text"
               :textcontent "foobar"}))

(defn ^:private get-nothing [& _]
  nil)

(def ^:private get-user-attributes
  {"applicant" {:commonName "Alice Applicant"
                :email "alice@applicant.com"}
   "handler" {:commonName "Hannah Handler"
              :email "hannah@handler.com"}})

(defn events-to-emails [events]
  (let [application (-> (reduce model/application-view nil events)
                        (model/enrich-with-injections {:get-workflow get-workflow
                                                       :get-catalogue-item get-catalogue-item
                                                       :get-form-template get-form-template
                                                       :get-license get-license
                                                       :get-user get-nothing
                                                       :get-users-with-role get-nothing
                                                       :get-attachments-for-application get-nothing}))]
    (with-redefs [rems.config/env (assoc rems.config/env :public-url "http://example.com/")
                  rems.db.users/get-user-attributes get-user-attributes
                  user-settings/get-user-settings (constantly {:language :en})]
      (mapv #(sort-emails (#'rems.poller.email/event-to-emails-impl % application)) events))))

(deftest test-event-to-emails-impl
  (let [base-events [{:application/id 7
                      :application/external-id "2001/3"
                      :event/type :application.event/created
                      :event/actor "applicant"
                      :application/resources [{:catalogue-item/id 10
                                               :resource/ext-id "urn:11"}
                                              {:catalogue-item/id 20
                                               :resource/ext-id "urn:21"}]
                      :workflow/id 5
                      :workflow/type :workflow/dynamic}
                     {:application/id 7
                      :event/type :application.event/draft-saved
                      :application/field-values {1 "Application title"}}
                     {:application/id 7
                      :event/type :application.event/submitted
                      :event/actor "applicant"
                      :event/time 13}]]
    (let [events (into
                  base-events
                  [{:application/id 7
                    :event/type :application.event/member-invited
                    :event/actor "applicant"
                    :application/member {:name "Some Body" :email "somebody@example.com"}
                    :invitation/token "abc"}
                   {:application/id 7
                    :event/type :application.event/comment-requested
                    :event/actor "handler"
                    :application/request-id "r1"
                    :application/commenters ["commenter1" "commenter2"]}
                   {:application/id 7
                    :event/type :application.event/member-joined
                    :event/actor "somebody"}
                   {:application/id 7
                    :event/type :application.event/commented
                    :event/actor "commenter2"
                    :application/request-id "r1"
                    :application/comment ["this is a comment"]}
                   {:application/id 7
                    :event/type :application.event/member-added
                    :event/actor "handler"
                    :application/member {:userid "member"}}
                   {:application/id 7
                    :event/type :application.event/licenses-added
                    :event/actor "handler"
                    :application/licenses [{:license/id 1234}]}
                   {:application/id 7
                    :event/type :application.event/decision-requested
                    :event/actor "assistant"
                    :application/request-id "r2"
                    :application/deciders ["decider"]}
                   {:application/id 7
                    :event/type :application.event/decided
                    :event/actor "decider"
                    :application/decision :approved}
                   {:application/id 7
                    :event/type :application.event/approved
                    :event/actor "handler"}
                   {:application/id 7
                    :event/type :application.event/closed
                    :event/actor "assistant"}])]
      (is (= [[]
              []
              [{:to-user "assistant"
                :subject "A new application has been submitted (2001/3, \"Application title\")"
                :body "Dear assistant,\n\nAlice Applicant has submitted a new application 2001/3, \"Application title\" to access resource(s) en title 11, en title 21.\n\nYou can view the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
               {:to-user "handler"
                :subject "A new application has been submitted (2001/3, \"Application title\")"
                :body "Dear Hannah Handler,\n\nAlice Applicant has submitted a new application 2001/3, \"Application title\" to access resource(s) en title 11, en title 21.\n\nYou can view the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]
              [{:to "somebody@example.com",
                :subject "Invitation to participate in an application",
                :body "Dear Some Body,\n\nYou have been invited to participate in an application submitted by Alice Applicant. The title of the application is 2001/3, \"Application title\".\n\nYou can view the application and accept the terms of use: http://example.com/accept-invitation?token=abc\n\nPlease do not reply to this automatically generated message."}]
              [{:to-user "commenter1"
                :subject "Review request (2001/3, \"Application title\")"
                :body "Dear commenter1,\n\nHannah Handler has requested your review on application 2001/3, \"Application title\" submitted by Alice Applicant.\n\nYou can review the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
               {:to-user "commenter2"
                :subject "Review request (2001/3, \"Application title\")"
                :body "Dear commenter2,\n\nHannah Handler has requested your review on application 2001/3, \"Application title\" submitted by Alice Applicant.\n\nYou can review the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]
              []
              [{:to-user "assistant"
                :subject "Application has been reviewed (2001/3, \"Application title\")"
                :body "Dear assistant,\n\ncommenter2 has reviewed the application 2001/3, \"Application title\" submitted by Alice Applicant.\n\nYou can view the application and the review: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
               {:to-user "handler"
                :subject "Application has been reviewed (2001/3, \"Application title\")"
                :body "Dear Hannah Handler,\n\ncommenter2 has reviewed the application 2001/3, \"Application title\" submitted by Alice Applicant.\n\nYou can view the application and the review: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]
              [{:to-user "member",
                :subject "Added as a member of an application (2001/3, \"Application title\")",
                :body "Dear member,\n\nYou've been added as a member of application 2001/3, \"Application title\".\n\nView application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]
              [{:to-user "applicant"
                :subject "New terms of use waiting for approval (2001/3, \"Application title\")"
                :body "Dear Alice Applicant,\n\nHannah Handler has requested your acceptance for new terms of use for application 2001/3, \"Application title\".\n\nYou can view the application and accept the terms of use: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
               {:to-user "member"
                :subject "New terms of use waiting for approval (2001/3, \"Application title\")"
                :body "Dear member,\n\nHannah Handler has requested your acceptance for new terms of use for application 2001/3, \"Application title\".\n\nYou can view the application and accept the terms of use: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
               {:to-user "somebody"
                :subject "New terms of use waiting for approval (2001/3, \"Application title\")"
                :body "Dear somebody,\n\nHannah Handler has requested your acceptance for new terms of use for application 2001/3, \"Application title\".\n\nYou can view the application and accept the terms of use: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]
              [{:to-user "decider",
                :subject "Decision requested (2001/3, \"Application title\")",
                :body "Dear decider,\n\nassistant has requested your decision on application 2001/3, \"Application title\" submitted by Alice Applicant.\n\nYou can review application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]
              [{:to-user "assistant",
                :subject "Decision notification (2001/3, \"Application title\")",
                :body "Dear assistant,\n\ndecider has sent a decision on application 2001/3, \"Application title\" submitted by Alice Applicant.\n\nYou can view the application and the decision: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
               {:to-user "handler",
                :subject "Decision notification (2001/3, \"Application title\")",
                :body "Dear Hannah Handler,\n\ndecider has sent a decision on application 2001/3, \"Application title\" submitted by Alice Applicant.\n\nYou can view the application and the decision: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]
              [{:to-user "applicant"
                :subject "Your application has been approved (2001/3, \"Application title\")"
                :body "Dear Alice Applicant,\n\nYour application 2001/3, \"Application title\" has been approved.\n\nYou can review the application and the decision: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
               {:to-user "assistant"
                :subject "Application approved (2001/3, \"Application title\")"
                :body "Dear assistant,\n\nHannah Handler has approved the application 2001/3, \"Application title\" from Alice Applicant.\n\nYou can review the application and the decision: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
               {:to-user "member"
                :subject "Your application has been approved (2001/3, \"Application title\")"
                :body "Dear member,\n\nYour application 2001/3, \"Application title\" has been approved.\n\nYou can review the application and the decision: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
               {:to-user "somebody"
                :subject "Your application has been approved (2001/3, \"Application title\")"
                :body "Dear somebody,\n\nYour application 2001/3, \"Application title\" has been approved.\n\nYou can review the application and the decision: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]
              [{:to-user "applicant"
                :subject "Your application has been closed (2001/3, \"Application title\")"
                :body "Dear Alice Applicant,\n\nYour application 2001/3, \"Application title\" has been closed.\n\nYou can review the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
               {:to-user "handler"
                :subject "Application closed (2001/3, \"Application title\")"
                :body "Dear Hannah Handler,\n\nassistant has closed the application 2001/3, \"Application title\" from Alice Applicant.\n\nYou can review the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
               {:to-user "member"
                :subject "Your application has been closed (2001/3, \"Application title\")"
                :body "Dear member,\n\nYour application 2001/3, \"Application title\" has been closed.\n\nYou can review the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
               {:to-user "somebody"
                :subject "Your application has been closed (2001/3, \"Application title\")"
                :body "Dear somebody,\n\nYour application 2001/3, \"Application title\" has been closed.\n\nYou can review the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]]
             (events-to-emails events))))
    (testing "application rejected"
      (let [events (conj base-events
                         {:application/id 7
                          :event/type :application.event/rejected
                          :event/actor "handler"})]
        (is (= [[]
                []
                [{:to-user "assistant"
                  :subject "A new application has been submitted (2001/3, \"Application title\")"
                  :body "Dear assistant,\n\nAlice Applicant has submitted a new application 2001/3, \"Application title\" to access resource(s) en title 11, en title 21.\n\nYou can view the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
                 {:to-user "handler"
                  :subject "A new application has been submitted (2001/3, \"Application title\")"
                  :body "Dear Hannah Handler,\n\nAlice Applicant has submitted a new application 2001/3, \"Application title\" to access resource(s) en title 11, en title 21.\n\nYou can view the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]
                [{:to-user "applicant"
                  :subject "Your application has been rejected (2001/3, \"Application title\")",
                  :body "Dear Alice Applicant,\n\nYour application 2001/3, \"Application title\" has been rejected.\n\nYou can review the application and the decision: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
                 {:to-user "assistant"
                  :subject "Application rejected (2001/3, \"Application title\")",
                  :body "Dear assistant,\n\nHannah Handler has rejected the application 2001/3, \"Application title\" from Alice Applicant.\n\nYou can review the application and the decision: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]]
               (events-to-emails events)))))
    (testing "id field can be overrided"
      (with-redefs [rems.config/env (assoc rems.config/env :application-id-column :id)]
        (is (= [[]
                []
                [{:to-user "assistant"
                  :subject "A new application has been submitted (7, \"Application title\")"
                  :body "Dear assistant,\n\nAlice Applicant has submitted a new application 7, \"Application title\" to access resource(s) en title 11, en title 21.\n\nYou can view the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
                 {:to-user "handler"
                  :subject "A new application has been submitted (7, \"Application title\")"
                  :body "Dear Hannah Handler,\n\nAlice Applicant has submitted a new application 7, \"Application title\" to access resource(s) en title 11, en title 21.\n\nYou can view the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]]
               (events-to-emails base-events)))))
    (testing "application title is optional"
      (is (= [[]
              [{:to-user "assistant"
                :subject "A new application has been submitted (2001/3)"
                :body "Dear assistant,\n\nAlice Applicant has submitted a new application 2001/3 to access resource(s) en title 11.\n\nYou can view the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
               {:to-user "handler"
                :subject "A new application has been submitted (2001/3)"
                :body "Dear Hannah Handler,\n\nAlice Applicant has submitted a new application 2001/3 to access resource(s) en title 11.\n\nYou can view the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]]
             (events-to-emails [{:application/id 7
                                 :application/external-id "2001/3"
                                 :event/type :application.event/created
                                 :event/actor "applicant"
                                 :application/resources [{:catalogue-item/id 10
                                                          :resource/ext-id "urn:11"}]
                                 :workflow/id 5
                                 :workflow/type :workflow/dynamic}
                                {:application/id 7
                                 :event/type :application.event/submitted
                                 :event/actor "applicant"}]))))
    (testing "returning application to applicant and re-submitting"
      (let [events (conj base-events
                         {:application/id 7
                          :event/type :application.event/returned
                          :event/actor "handler"
                          :application/comment ["requesting changes"]}
                         {:application/id 7
                          :event/type :application.event/submitted
                          :event/actor "applicant"})]
        (is (= [[]
                []
                [{:to-user "assistant"
                  :subject "A new application has been submitted (2001/3, \"Application title\")"
                  :body "Dear assistant,\n\nAlice Applicant has submitted a new application 2001/3, \"Application title\" to access resource(s) en title 11, en title 21.\n\nYou can view the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
                 {:to-user "handler"
                  :subject "A new application has been submitted (2001/3, \"Application title\")"
                  :body "Dear Hannah Handler,\n\nAlice Applicant has submitted a new application 2001/3, \"Application title\" to access resource(s) en title 11, en title 21.\n\nYou can view the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]
                [{:to-user "applicant"
                  :subject "Application has been returned for modifications (2001/3, \"Application title\")"
                  :body "Dear Alice Applicant,\n\nYour application 2001/3, \"Application title\" has been returned to you for modifications.\n\nYou can modify and re-submit the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
                 {:to-user "assistant"
                  :subject "Application has been returned for modifications (2001/3, \"Application title\")"
                  :body "Dear assistant,\n\nHannah Handler has returned the application 2001/3, \"Application title\" for modifications to the applicant Alice Applicant.\n\nView application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]
                [{:to-user "assistant"
                  :subject "Application has been re-submitted (2001/3, \"Application title\")"
                  :body "Dear assistant,\n\nApplication 2001/3, \"Application title\" has been re-submitted by Alice Applicant.\n\nYou can view the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}
                 {:to-user "handler"
                  :subject "Application has been re-submitted (2001/3, \"Application title\")"
                  :body "Dear Hannah Handler,\n\nApplication 2001/3, \"Application title\" has been re-submitted by Alice Applicant.\n\nYou can view the application: http://example.com/#/application/7\n\nPlease do not reply to this automatically generated message."}]]
               (events-to-emails events)))))))
