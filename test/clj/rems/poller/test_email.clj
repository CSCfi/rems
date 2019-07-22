(ns rems.poller.test-email
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.application.model :as model]
            [rems.config]
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
  {5 {:workflow {:handlers ["handler" "assistant"]}}})

(defn ^:private get-nothing [& _]
  nil)

(defn events-to-emails [events]
  (let [application (-> (reduce model/application-view nil events)
                        (model/enrich-with-injections {:get-workflow get-workflow
                                                       :get-catalogue-item get-catalogue-item
                                                       :get-form-template get-nothing
                                                       :get-license get-nothing
                                                       :get-user get-nothing
                                                       :get-users-with-role get-nothing
                                                       :get-attachments-for-application get-nothing}))]
    (with-redefs [rems.config/env (assoc rems.config/env :public-url "http://example.com/")]
      (text/with-language :en
        (fn [] (mapv #(sort-emails (#'rems.poller.email/event-to-emails-impl % application)) events))))))

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
                      :event/type :application.event/submitted
                      :event/actor "applicant"}]]
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
              [{:to-user "assistant",
                :subject "Application submitted (applicant: 2001/3)",
                :body "Dear assistant,\n\napplicant has submitted an application (2001/3): en title 11, en title 21.\n\nView application: http://example.com/#/application/7"}
               {:to-user "handler",
                :subject "Application submitted (applicant: 2001/3)",
                :body "Dear handler,\n\napplicant has submitted an application (2001/3): en title 11, en title 21.\n\nView application: http://example.com/#/application/7"}]
              [{:to "somebody@example.com",
                :subject "Invitation to participate in an application",
                :body "Hello,\n\nThis email address (somebody@example.com) has been invited to participate in an application.\n\nParticipate: http://example.com/accept-invitation?token=abc"}]
              [{:to-user "commenter1",
                :subject "Comment requested (handler: 2001/3)",
                :body "Dear commenter1,\n\nhandler has requested a comment on application 2001/3.\n\nComment: http://example.com/#/application/7"}
               {:to-user "commenter2",
                :subject "Comment requested (handler: 2001/3)",
                :body "Dear commenter2,\n\nhandler has requested a comment on application 2001/3.\n\nComment: http://example.com/#/application/7"}]
              []
              [{:to-user "assistant",
                :subject "Comment added (commenter2: 2001/3)",
                :body "Dear assistant,\n\ncommenter2 has commented application 2001/3.\n\nView application: http://example.com/#/application/7"}
               {:to-user "handler",
                :subject "Comment added (commenter2: 2001/3)",
                :body "Dear handler,\n\ncommenter2 has commented application 2001/3.\n\nView application: http://example.com/#/application/7"}]
              [{:to-user "member",
                :subject "Added as a member of an application (2001/3)",
                :body "Dear member,\n\nYou've been added as a member of application 2001/3.\n\nView application: http://example.com/#/application/7"}]
              [{:to-user "decider",
                :subject "Decision requested (assistant: 2001/3)",
                :body "Dear decider,\n\nassistant has requested your decision on application 2001/3.\n\nView application: http://example.com/#/application/7"}]
              [{:to-user "assistant",
                :subject "Decision made (decider: 2001/3)",
                :body "Dear assistant,\n\ndecider has made a decision on application 2001/3.\n\nView application: http://example.com/#/application/7"}
               {:to-user "handler",
                :subject "Decision made (decider: 2001/3)",
                :body "Dear handler,\n\ndecider has made a decision on application 2001/3.\n\nView application: http://example.com/#/application/7"}]
              [{:to-user "applicant",
                :subject "Application approved (2001/3)",
                :body "Dear applicant,\n\nYour application 2001/3 has been approved.\n\nView application: http://example.com/#/application/7"}
               {:to-user "assistant"
                :subject "Application approved (2001/3)"
                :body "Dear assistant,\n\nhandler has approved the application 2001/3 from applicant.\n\nView application: http://example.com/#/application/7"}
               {:to-user "member",
                :subject "Application approved (2001/3)",
                :body "Dear member,\n\nYour application 2001/3 has been approved.\n\nView application: http://example.com/#/application/7"}
               {:to-user "somebody",
                :subject "Application approved (2001/3)",
                :body "Dear somebody,\n\nYour application 2001/3 has been approved.\n\nView application: http://example.com/#/application/7"}]
              [{:to-user "applicant",
                :subject "Application closed (2001/3)",
                :body "Dear applicant,\n\nYour application 2001/3 has been closed.\n\nView application: http://example.com/#/application/7"}
               {:to-user "handler"
                :subject "Application closed (2001/3)"
                :body "Dear handler,\n\nassistant has closed the application 2001/3 from applicant.\n\nView application: http://example.com/#/application/7"}
               {:to-user "member",
                :subject "Application closed (2001/3)",
                :body "Dear member,\n\nYour application 2001/3 has been closed.\n\nView application: http://example.com/#/application/7"}
               {:to-user "somebody",
                :subject "Application closed (2001/3)",
                :body "Dear somebody,\n\nYour application 2001/3 has been closed.\n\nView application: http://example.com/#/application/7"}]]
             (events-to-emails events))))
    (testing "application rejected"
      (let [events (conj base-events
                         {:application/id 7
                          :event/type :application.event/rejected
                          :event/actor "handler"})]
        (is (= [[]
                [{:to-user "assistant",
                  :subject "Application submitted (applicant: 2001/3)",
                  :body "Dear assistant,\n\napplicant has submitted an application (2001/3): en title 11, en title 21.\n\nView application: http://example.com/#/application/7"}
                 {:to-user "handler",
                  :subject "Application submitted (applicant: 2001/3)",
                  :body "Dear handler,\n\napplicant has submitted an application (2001/3): en title 11, en title 21.\n\nView application: http://example.com/#/application/7"}]
                [{:subject "Application rejected (2001/3)",
                  :body "Dear applicant,\n\nYour application 2001/3 has been rejected.\n\nView application: http://example.com/#/application/7",
                  :to-user "applicant"}
                 {:subject "Application rejected (2001/3)"
                  :body "Dear assistant,\n\nhandler has rejected the application 2001/3 from applicant.\n\nView application: http://example.com/#/application/7"
                  :to-user "assistant"}]]
               (events-to-emails events)))))
    (testing "id field can be overrided"
      (with-redefs [rems.config/env (assoc rems.config/env :application-id-column :id)]
        (is (= [[]
                [{:to-user "assistant"
                  :subject "Application submitted (applicant: 7)"
                  :body "Dear assistant,\n\napplicant has submitted an application (7): en title 11, en title 21.\n\nView application: http://example.com/#/application/7"}
                 {:to-user "handler"
                  :subject "Application submitted (applicant: 7)"
                  :body "Dear handler,\n\napplicant has submitted an application (7): en title 11, en title 21.\n\nView application: http://example.com/#/application/7"}]]
               (events-to-emails base-events)))))
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
                [{:to-user "assistant",
                  :subject "Application submitted (applicant: 2001/3)",
                  :body "Dear assistant,\n\napplicant has submitted an application (2001/3): en title 11, en title 21.\n\nView application: http://example.com/#/application/7"}
                 {:to-user "handler",
                  :subject "Application submitted (applicant: 2001/3)",
                  :body "Dear handler,\n\napplicant has submitted an application (2001/3): en title 11, en title 21.\n\nView application: http://example.com/#/application/7"}]
                [{:to-user "applicant",
                  :subject "Application returned (2001/3)",
                  :body "Dear applicant,\n\nYour application 2001/3 has been returned.\n\nView application: http://example.com/#/application/7"}
                 {:to-user "assistant",
                  :subject "Application returned (2001/3)",
                  :body "Dear assistant,\n\nhandler has returned the application 2001/3 from applicant.\n\nView application: http://example.com/#/application/7"}]
                [{:to-user "assistant",
                  :subject "Application submitted (applicant: 2001/3)",
                  :body "Dear assistant,\n\napplicant has submitted an application (2001/3): en title 11, en title 21.\n\nView application: http://example.com/#/application/7"}
                 {:to-user "handler",
                  :subject "Application submitted (applicant: 2001/3)",
                  :body "Dear handler,\n\napplicant has submitted an application (2001/3): en title 11, en title 21.\n\nView application: http://example.com/#/application/7"}]]
               (events-to-emails events)))))))
