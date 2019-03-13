(ns rems.test.poller.email
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            rems.config
            rems.locales
            [rems.poller.email :refer :all]
            [rems.text :as text]
            [rems.workflow.dynamic :as dynamic]))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'rems.config/env #'rems.locales/translations)
    (f)
    (mount/stop)))

(deftest test-event-to-emails-impl
  (let [events [{:application/id 7
                 :event/type :application.event/created
                 :event/actor "applicant"
                 :workflow/type :workflow/dynamic
                 :workflow.dynamic/handlers #{"handler" "assistant"}}
                {:application/id 7
                 :event/type :application.event/submitted
                 :event/actor "applicant"}
                {:application/id 7
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
                 :event/actor "assistant"}]
        application (dynamic/apply-events nil events)]
    (is (= [[] ;; created
            [] ;; submitted
            [{:to "somebody@example.com",
              :subject "Invitation to participate in an application",
              :body "Hello,\nThis email address (somebody@example.com) has been invited to participate in an application.\nParticipate with this link: http://localhost:3001/accept-invitation?token=abc"}]
            [{:to-user "commenter1",
              :subject "Comment request",
              :body "Dear commenter1,\nUser handler has requested your comment on application 7.\nComment here: http://localhost:3001/#/application/7"}
             {:to-user "commenter2",
              :subject "Comment request",
              :body "Dear commenter2,\nUser handler has requested your comment on application 7.\nComment here: http://localhost:3001/#/application/7"}]
            []
            [{:to-user "handler",
              :subject "New comment notification",
              :body "Dear handler,\nUser commenter2 has posted a comment on application 7.\nView the application: http://localhost:3001/#/application/7"}
             {:to-user "assistant",
              :subject "New comment notification",
              :body "Dear assistant,\nUser commenter2 has posted a comment on application 7.\nView the application: http://localhost:3001/#/application/7"}]
            [{:to-user "member",
              :subject "You've been added as a member to an application",
              :body "Dear member,\nYou've been added as a member to application 7.\nView the application: http://localhost:3001/#/application/7"}]
            [{:to-user "decider",
              :subject "Decision request",
              :body "Dear decider,\nUser assistant has requested your decision on application 7.\nView the application: http://localhost:3001/#/application/7"}]
            [{:to-user "handler",
              :subject "New decision notification",
              :body "Dear handler,\nUser decider has sent a decision on application 7.\nView the application: http://localhost:3001/#/application/7"}
             {:to-user "assistant",
              :subject "New decision notification",
              :body "Dear assistant,\nUser decider has sent a decision on application 7.\nView the application: http://localhost:3001/#/application/7"}]
            [{:to-user "applicant",
              :subject "Your application has been approved",
              :body "Dear applicant,\nYour application  has been approved.\nView your application: http://localhost:3001/#/application/"}
             {:to-user "somebody",
              :subject "Your application has been approved",
              :body "Dear somebody,\nYour application  has been approved.\nView your application: http://localhost:3001/#/application/"}
             {:to-user "member",
              :subject "Your application has been approved",
              :body "Dear member,\nYour application  has been approved.\nView your application: http://localhost:3001/#/application/"}]
            [{:to-user "applicant",
              :subject "Your application has been closed",
              :body "Dear applicant,\nYour application  has been closed.\nView your application: http://localhost:3001/#/application/"}
              {:to-user "somebody",
               :subject "Your application has been closed",
               :body "Dear somebody,\nYour application  has been closed.\nView your application: http://localhost:3001/#/application/"}
              {:to-user "member",
               :subject "Your application has been closed",
               :body "Dear member,\nYour application  has been closed.\nView your application: http://localhost:3001/#/application/"}]]
           (text/with-language :en
             (fn [] (mapv #(#'rems.poller.email/event-to-emails-impl % application) events)))))))
