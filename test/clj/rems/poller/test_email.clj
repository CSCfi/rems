(ns rems.poller.test-email
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.application.model :as model]
            [rems.config]
            [rems.db.user-settings :as user-settings]
            [rems.locales]
            [rems.poller.email :refer :all]))

(defn empty-signature [f]
  (assoc rems.config/env :t.email/regards "")
  (f))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'rems.config/env
                 #'rems.locales/translations)
    (f)
    (mount/stop))
  :each empty-signature)

(deftest test-send-email!
  ;; Just for a bit of coverage in code that doesn't get run in other tests or the dev profile
  (let [message-atom (atom nil)]
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :smtp-host "localhost"
                                         :smtp-port 25
                                         :mail-from "rems@rems.rems")
                  postal.core/send-message (fn [_host message] (reset! message-atom message))
                  rems.db.users/get-user (constantly {:email "user@example.com"})]

      (testing "mail to email address"
        (send-email! {:to "foo@example.com" :subject "ding" :body "boing"})
        (is (= {:to "foo@example.com"
                :subject "ding"
                :body "boing"
                :from "rems@rems.rems"}
               @message-atom))
        (reset! message-atom nil))

      (testing "mail to user"
        (send-email! {:to-user "user" :subject "x" :body "y"})
        (is (= {:to "user@example.com"
                :to-user "user"
                :subject "x"
                :body "y"
                :from "rems@rems.rems"}
               @message-atom))
        (reset! message-atom nil))

      (testing "mail to user without email"
        (with-redefs [rems.db.users/get-user (constantly {:email nil})]
          (send-email! {:to-user "user" :subject "x" :body "y"}))
        (is (nil? @message-atom))
        (reset! message-atom nil)))))

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
                             :name "Hannah Handler"
                             :email "handler@example.com"}
                            {:userid "assistant"
                             :name "Amber Assistant"
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

(defn- get-user [userid]
  (case userid
    "applicant" {:userid "applicant"
                 :name "Alice Applicant"
                 :email "alice@applicant.com"}
    "handler" {:userid "handler"
               :name "Hannah Handler"
               :email "hannah@handler.com"}
    "assistant" {:userid "assistant"
                 :name "Amber Assistant"
                 :email "assistant@example.com"}
    {:userid userid}))

(defn email-recipient [email]
  (or (:to email) (:to-user email)))

(defn sort-emails [emails]
  (sort-by email-recipient emails))

(defn email-recipients [emails]
  (set (mapv email-recipient emails)))

(defn email-to [user emails]
  ;; return arbitrary email if none match to get better errors from tests
  (or (first (filter #(= user (email-recipient %)) emails))
      (first emails)))

(defn emails
  ([lang base-events event]
   (let [all-events (concat base-events [event])
         application (-> (reduce model/application-view nil all-events)
                         (model/enrich-with-injections {:get-workflow get-workflow
                                                        :get-catalogue-item get-catalogue-item
                                                        :get-form-template get-form-template
                                                        :get-license get-license
                                                        :get-user get-user
                                                        :get-users-with-role get-nothing
                                                        :get-attachments-for-application get-nothing}))]
     (with-redefs [rems.config/env (assoc rems.config/env :public-url "http://example.com/")
                   user-settings/get-user-settings (fn [userid]
                                                     (assert (string? userid))
                                                     {:language lang})]
       (sort-emails (#'rems.poller.email/event-to-emails-impl
                     (model/enrich-event event get-user #{})
                     application)))))
  ([base-events event]
   (emails :en base-events event)))

(def created-events [{:application/id 7
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
                      :application/field-values {1 "Application title"}}])

(def submit-event {:application/id 7
                   :event/type :application.event/submitted
                   :event/actor "applicant"
                   :event/time 13})

(def base-events (conj created-events submit-event))

(deftest test-submitted
  (let [mails (emails created-events submit-event)]
    (is (= #{"assistant" "handler"} (email-recipients mails)))
    (is (= {:to-user "assistant"
            :subject "(2001/3, \"Application title\") A new application has been submitted"
            :body "Dear Amber Assistant,\n\nAlice Applicant has submitted a new application 2001/3, \"Application title\" to access resource(s) en title 11, en title 21.\n\nYou can review the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
           (email-to "assistant" mails)))))

(deftest test-member-invited
  (is (= [{:to "somebody@example.com"
           :subject "Invitation to participate in application 2001/3, \"Application title\""
           :body "Dear Some Body,\n\nYou have been invited to participate in application 2001/3, \"Application title\", by Alice Applicant.\n\nYou can view the application and accept the terms of use at http://example.com/accept-invitation?token=abc\n\nPlease do not reply to this automatically generated message."}]
         (emails base-events
                 {:application/id 7
                  :event/type :application.event/member-invited
                  :event/actor "applicant"
                  :application/member {:name "Some Body" :email "somebody@example.com"}
                  :invitation/token "abc"}))))

(deftest test-commenting
  (let [request {:application/id 7
                 :event/type :application.event/comment-requested
                 :event/actor "handler"
                 :application/request-id "r1"
                 :application/commenters ["commenter1" "commenter2"]}
        requested-events (conj base-events request)]
    (testing "comment-request"
      (let [mails (emails base-events request)]
        (is (= #{"commenter1" "commenter2"} (email-recipients mails)))
        (is (= {:to-user "commenter1"
                :subject "(2001/3, \"Application title\") Review request"
                :body "Dear commenter1,\n\nHannah Handler has requested your review on application 2001/3, \"Application title\", submitted by Alice Applicant.\n\nYou can review the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
               (email-to "commenter1" mails)))))
    (testing "commented"
      (let [mails (emails requested-events {:application/id 7
                                            :event/type :application.event/commented
                                            :event/actor "commenter2"
                                            :application/request-id "r1"
                                            :application/comment "this is a comment"})]
        (is (= #{"assistant" "handler"} (email-recipients mails)))
        (is (= {:to-user "assistant"
                :subject "(2001/3, \"Application title\") Application has been reviewed"
                :body "Dear Amber Assistant,\n\ncommenter2 has reviewed the application 2001/3, \"Application title\", submitted by Alice Applicant.\n\nYou can review the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
               (email-to "assistant" mails)))))))

(deftest test-remarked
  (let [mails (emails base-events {:application/id 7
                                   :event/type :application.event/remarked
                                   :event/actor "remarker"
                                   :application/comment "remark!"})]
    (is (= #{"assistant" "handler"} (email-recipients mails)))
    (is (= {:to-user "assistant"
            :subject "(2001/3, \"Application title\") Application has been commented"
            :body "Dear Amber Assistant,\n\nremarker has commented on the application 2001/3, \"Application title\", submitted by Alice Applicant.\n\nYou can review the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
           (email-to "assistant" mails)))))

(deftest test-members-licenses-approved-closed
  (let [add-member {:application/id 7
                    :event/type :application.event/member-added
                    :event/actor "handler"
                    :application/member {:userid "member"}}
        join {:application/id 7
              :event/type :application.event/member-joined
              :event/actor "somebody"}
        member-events (conj base-events add-member join)]
    (testing "member-added"
      (is (= [{:to-user "member",
               :subject "Added as a member of an application 2001/3, \"Application title\"",
               :body "Dear member,\n\nYou've been added as a member of application 2001/3, \"Application title\", submitted by Alice Applicant.\n\nYou can view application and accept the terms of use at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}]
             (emails base-events add-member))))
    (testing "licenses-added"
      (let [mails (emails member-events {:application/id 7
                                         :event/type :application.event/licenses-added
                                         :event/actor "handler"
                                         :application/licenses [{:license/id 1234}]})]
        (is (= #{"applicant" "member" "somebody"} (email-recipients mails)))
        (is (= {:to-user "applicant"
                :subject "Your application's 2001/3, \"Application title\" terms of use have changed"
                :body "Dear Alice Applicant,\n\nHannah Handler has requested your approval for changed terms of use to application 2001/3, \"Application title\".\n\nYou can review the application and approve the changed the terms of use at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
               (email-to "applicant" mails)))))
    (testing "approved"
      (let [mails (emails member-events {:application/id 7
                                         :event/type :application.event/approved
                                         :event/actor "handler"})]
        (is (= #{"applicant" "member" "somebody" "assistant"} (email-recipients mails)))
        (is (= {:to-user "applicant"
                :subject "Your application 2001/3, \"Application title\" has been approved"
                :body "Dear Alice Applicant,\n\nYour application 2001/3, \"Application title\" has been approved.\n\nYou can view the application and the decision at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
               (email-to "applicant" mails)))
        (is (= {:to-user "member"
                :subject "Your application 2001/3, \"Application title\" has been approved"
                :body "Dear member,\n\nYour application 2001/3, \"Application title\" has been approved.\n\nYou can view the application and the decision at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
               (email-to "member" mails)))
        (is (= {:to-user "assistant"
                :subject "(2001/3, \"Application title\") Application has been approved"
                :body "Dear Amber Assistant,\n\nHannah Handler has approved the application 2001/3, \"Application title\" from Alice Applicant.\n\nYou can view the application and the decision at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
               (email-to "assistant" mails)))))
    (testing "closed"
      (let [mails (emails member-events {:application/id 7
                                         :event/type :application.event/closed
                                         :event/actor "assistant"})]
        (is (= #{"applicant" "member" "somebody" "handler"} (email-recipients mails)))
        (is (= {:to-user "applicant"
                :subject "Your application 2001/3, \"Application title\" has been closed"
                :body "Dear Alice Applicant,\n\nYour application 2001/3, \"Application title\" has been closed.\n\nYou can view the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
               (email-to "applicant" mails)))
        (is (= {:to-user "handler"
                :subject "(2001/3, \"Application title\") Application has been closed"
                :body "Dear Hannah Handler,\n\nAmber Assistant has closed the application 2001/3, \"Application title\" from Alice Applicant.\n\nYou can view the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
               (email-to "handler" mails)))))))

(deftest test-decisions
  (let [decision-request {:application/id 7
                          :event/type :application.event/decision-requested
                          :event/actor "assistant"
                          :application/request-id "r2"
                          :application/deciders ["decider"]}
        requested-events (conj base-events decision-request)]
    (testing "decision-requested"
      (is (= [{:to-user "decider",
               :subject "(2001/3, \"Application title\") Decision request",
               :body "Dear decider,\n\nAmber Assistant has requested your decision on application 2001/3, \"Application title\", submitted by Alice Applicant.\n\nYou can review application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}]
             (emails base-events decision-request))))
    (testing "decided"
      (let [mails (emails requested-events {:application/id 7
                                            :event/type :application.event/decided
                                            :event/actor "decider"
                                            :application/decision :approved})]
        (is (= #{"assistant" "handler"} (email-recipients mails)))
        (is (= {:to-user "assistant",
                :subject "(2001/3, \"Application title\") Application decision notification",
                :body "Dear Amber Assistant,\n\ndecider has filed a decision on application 2001/3, \"Application title\", submitted by Alice Applicant.\n\nYou can review the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
               (email-to "assistant" mails)))))))

(deftest test-rejected
  (is (= [{:to-user "applicant"
           :subject "Your application 2001/3, \"Application title\" has been rejected",
           :body "Dear Alice Applicant,\n\nYour application 2001/3, \"Application title\" has been rejected.\n\nYou can view the application and the decision at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
          {:to-user "assistant"
           :subject "(2001/3, \"Application title\") Application has been rejected",
           :body "Dear Amber Assistant,\n\nHannah Handler has rejected the application 2001/3, \"Application title\" from Alice Applicant.\n\nYou can view the application and the decision at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}]
         (emails base-events {:application/id 7
                              :event/type :application.event/rejected
                              :event/actor "handler"}))))

(deftest test-revoked
  (is (= [{:to-user "applicant"
           :subject "Your application 2001/3, \"Application title\" has been revoked",
           :body "Dear Alice Applicant,\n\nYour application 2001/3, \"Application title\" has been revoked.\n\nYou can view the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
          {:to-user "assistant"
           :subject "(2001/3, \"Application title\") Application has been revoked",
           :body "Dear Amber Assistant,\n\nHannah Handler has revoked the application 2001/3, \"Application title\" from Alice Applicant.\n\nYou can view the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}]
         (emails base-events {:application/id 7
                              :event/type :application.event/revoked
                              :event/actor "handler"}))))

(deftest test-id-field
  (with-redefs [rems.config/env (assoc rems.config/env :application-id-column :id)]
    (is (= {:to-user "assistant"
            :subject "(7, \"Application title\") A new application has been submitted"
            :body "Dear Amber Assistant,\n\nAlice Applicant has submitted a new application 7, \"Application title\" to access resource(s) en title 11, en title 21.\n\nYou can review the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
           (email-to "assistant" (emails created-events submit-event))))))

(deftest test-title-optional
  (is (= {:to-user "assistant"
          :subject "(2001/3) A new application has been submitted"
          :body "Dear Amber Assistant,\n\nAlice Applicant has submitted a new application 2001/3 to access resource(s) en title 11.\n\nYou can review the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
         (email-to "assistant"
                   (emails [{:application/id 7
                             :application/external-id "2001/3"
                             :event/type :application.event/created
                             :event/actor "applicant"
                             :application/resources [{:catalogue-item/id 10
                                                      :resource/ext-id "urn:11"}]
                             :workflow/id 5
                             :workflow/type :workflow/dynamic}]
                           {:application/id 7
                            :event/type :application.event/submitted
                            :event/actor "applicant"})))))

(deftest test-return-resubmit
  (let [add-member {:application/id 7
                    :event/type :application.event/member-added
                    :event/actor "handler"
                    :application/member {:userid "member"}}
        added (conj base-events add-member)
        return {:application/id 7
                :event/type :application.event/returned
                :event/actor "handler"
                :application/comment ["requesting changes"]}
        returned-events (conj added return)
        resubmit {:application/id 7
                  :event/type :application.event/submitted
                  :event/actor "applicant"}]
    (is (= [{:to-user "applicant"
             :subject "Your application 2001/3, \"Application title\" needs to be amended"
             :body "Dear Alice Applicant,\n\nYour application 2001/3, \"Application title\" has been returned for your consideration. Please, amend according to requests and resubmit.\n\nYou can review the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
            {:to-user "assistant"
             :subject "(2001/3, \"Application title\") Application has been returned to applicant"
             :body "Dear Amber Assistant,\n\nHannah Handler has returned the application 2001/3, \"Application title\" to the applicant Alice Applicant for modifications.\n\nYou can view the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}]
           (emails added return)))
    (let [mails (emails returned-events resubmit)]
      (is (= #{"assistant" "handler"} (email-recipients mails)))
      (is (= {:to-user "assistant"
              :subject "(2001/3, \"Application title\") Application has been resubmitted"
              :body "Dear Amber Assistant,\n\nApplication 2001/3, \"Application title\" has been resubmitted by Alice Applicant.\n\nYou can review the application at http://example.com/application/7\n\nPlease do not reply to this automatically generated message."}
             (email-to "assistant" mails))))))

(deftest test-finnish-emails
  ;; only one test case so far, more of a smoke test
  (testing "submitted"
    (let [mails (emails :fi created-events submit-event)]
      (is (= #{"assistant" "handler"} (email-recipients mails)))
      (is (= {:to-user "handler"
              :subject "(2001/3, \"Application title\") Uusi hakemus"
              :body "Hyvä Hannah Handler,\n\nAlice Applicant on lähettänyt käyttöoikeushakemuksen 2001/3, \"Application title\" resurss(e)ille fi title 11, fi title 21.\n\nVoit tarkastella hakemusta osoitteessa http://example.com/application/7\n\nTämä on automaattinen viesti. Älä vastaa."}
             (email-to "handler" mails))))))
