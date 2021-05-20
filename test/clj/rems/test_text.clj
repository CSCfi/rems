(ns rems.test-text
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.application.events :as events]
            [rems.application.model :as model]
            [rems.config :refer [env]]
            [rems.test-locales :refer [loc-en]]
            [rems.text :refer [with-language localize-event localize-state]]))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'rems.config/env #'rems.locales/translations)
    (f)
    (mount/stop)))

(defn- valid-localization? [str]
  (and (not (.contains str "Missing"))
       (not (.contains str "Unknown"))))

(deftest test-all-state-localizations
  (is (= (-> (:states (:applications (:t (loc-en))))
             (dissoc :unknown)
             (keys)
             (sort))
         (->> model/states
              (map name)
              (map keyword)
              (sort))))
  (with-language :en
    (fn []
      (is (not (valid-localization? (localize-state "foobar"))))
      (doseq [s model/states]
        (testing s
          (is (valid-localization? (localize-state s))))))))

(deftest test-all-event-localizations
  (let [event-types (keys events/event-schemas)]
    (is (= (-> (:events (:applications (:t (loc-en))))
               (dissoc :unknown)
               (keys)
               (sort))
           (->> event-types
                (map name)
                (map keyword)
                (sort))))
    (with-language :en
      (fn []
        (is (not (valid-localization? (localize-event {:event/type "foobar"}))))
        (doseq [event-type event-types]
          (testing event-type
            (is (valid-localization? (localize-event
                                      (merge {:event/type event-type}
                                             (when (= event-type :application.event/decided)
                                               {:application/decision :approved})))))))))))

(deftest test-localize-event
  ;; check some event types that have special logic
  (with-language :en
    (fn []
      (is (= "Bob Bond added User Urquhart to the application."
             (localize-event {:event/type :application.event/member-added
                              :event/actor "bob"
                              :event/actor-attributes {:userid "bob"
                                                       :name "Bob Bond"}
                              :application/member {:userid "User"
                                                   :email "user@example.com"
                                                   :name "User Urquhart"}})))
      (is (= "Bob Bond changed the applicant to User Urquhart."
             (localize-event {:event/type :application.event/applicant-changed
                              :event/actor "bob"
                              :event/actor-attributes {:userid "bob"
                                                       :name "Bob Bond"}
                              :application/applicant {:userid "User"
                                                      :email "user@example.com"
                                                      :name "User Urquhart"}})))
      (is (= "Bob Bond invited User Urquhart <user@example.com> to the application."
             (localize-event {:event/type :application.event/member-invited
                              :event/actor "bob"
                              :event/actor-attributes {:userid "bob"
                                                       :name "Bob Bond"}
                              :application/member {:email "user@example.com"
                                                   :name "User Urquhart"}})))
      (is (= "Bob Bond invited User Urquhart <user@example.com> to review the application."
             (localize-event {:event/type :application.event/reviewer-invited
                              :event/actor "bob"
                              :event/actor-attributes {:userid "bob"
                                                       :name "Bob Bond"}
                              :application/reviewer {:email "user@example.com"
                                                     :name "User Urquhart"}})))
      (is (= "Bob Bond invited User Urquhart <user@example.com> to decide."
             (localize-event {:event/type :application.event/decider-invited
                              :event/actor "bob"
                              :event/actor-attributes {:userid "bob"
                                                       :name "Bob Bond"}
                              :application/decider {:email "user@example.com"
                                                    :name "User Urquhart"}})))
      (is (= "Bob Bond approved the application. Access rights end 2020-01-02."
             (localize-event {:event/type :application.event/approved
                              :event/actor "bob"
                              :event/actor-attributes {:userid "bob"
                                                       :name "Bob Bond"}
                              :entitlement/end (time/date-time 2020 1 2)}))))))
