(ns rems.text
  (:require [clojure.test :refer [deftest is]]
            #?(:clj [clj-time.core :as time]
               :cljs [cljs-time.core :as time])
            #?(:clj [clj-time.format :as time-format]
               :cljs [cljs-time.format :as time-format])
            #?(:cljs [cljs-time.coerce :as time-coerce])
            [clojure.string :as str]
            #?(:cljs [re-frame.core :as rf])
            [rems.common.application-util :as application-util]
            #?(:clj [rems.context :as context])
            #?(:clj [rems.locales :as locales])
            [taoensso.tempura :refer [tr]]))

(defn with-language [lang f]
  (assert (keyword? lang) {:lang lang})
  #?(:clj (binding [context/*lang* lang
                    context/*tempura* (partial tr (locales/tempura-config) [lang])]
            (f))))

(defn- failsafe-fallback
  "Fallback for when loading the translations has failed."
  [k args]
  (pr-str (vec (if (= :t/missing k)
                 (first args)
                 (cons k args)))))

(defn text-format
  "Return the tempura translation for a given key & time arguments"
  [k & args]
  #?(:clj (context/*tempura* [k :t/missing] (vec args))
     :cljs (let [translations (rf/subscribe [:translations])
                 language (rf/subscribe [:language])]
             (tr {:dict @translations}
                 [@language]
                 [k :t/missing (failsafe-fallback k args)]
                 (vec args)))))

(defn text-no-fallback
  "Return the tempura translation for a given key. Additional fallback
  keys can be given but there is no default fallback text."
  [& ks]
  #?(:clj (context/*tempura* (vec ks))
     :cljs (let [translations (rf/subscribe [:translations])
                 language (rf/subscribe [:language])]
             (try
               (tr {:dict @translations}
                   [@language]
                   (vec ks))
               (catch js/Object e
                 ;; fail gracefully if the re-frame state is incomplete
                 (.error js/console e)
                 (str (vec ks)))))))

(defn text
  "Return the tempura translation for a given key. Additional fallback
  keys can be given."
  [& ks]
  #?(:clj (apply text-no-fallback (conj (vec ks) (text-format :t/missing (vec ks))))
     ;; NB: we can't call the text-no-fallback here as in CLJS
     ;; we can both call this as function or use as a React component
     :cljs (let [translations (rf/subscribe [:translations])
                 language (rf/subscribe [:language])]
             (try
               (tr {:dict @translations}
                   [@language]
                   (conj (vec ks) (text-format :t/missing (vec ks))))
               (catch js/Object e
                 ;; fail gracefully if the re-frame state is incomplete
                 (.error js/console e)
                 (str (vec ks)))))))

(defn localized [m]
  (let [lang #?(:clj context/*lang*
                :cljs @(rf/subscribe [:language]))]
    (or (get m lang)
        (first (vals m)))))

;; TODO: replace usages of `get-localized-title` with `localized`
(defn get-localized-title [item language]
  (or (get-in item [:localizations language :title])
      (:title (first (vals (get item :localizations))))))

(def ^:private states
  {:application.state/draft :t.applications.states/draft
   :application.state/submitted :t.applications.states/submitted
   :application.state/approved :t.applications.states/approved
   :application.state/rejected :t.applications.states/rejected
   :application.state/closed :t.applications.states/closed
   :application.state/returned :t.applications.states/returned
   :application.state/revoked :t.applications.states/revoked})

(defn localize-state [state]
  (text (get states state :t.applications.states/unknown)))

(def ^:private todos
  {:new-application :t.applications.todos/new-application
   :no-pending-requests :t.applications.todos/no-pending-requests
   :resubmitted-application :t.applications.todos/resubmitted-application
   :waiting-for-decision :t.applications.todos/waiting-for-decision
   :waiting-for-review :t.applications.todos/waiting-for-review
   :waiting-for-your-decision :t.applications.todos/waiting-for-your-decision
   :waiting-for-your-review :t.applications.todos/waiting-for-your-review})

(defn localize-todo [todo]
  (if (nil? todo)
    ""
    (text (get todos todo :t.applications.todos/unknown))))

(defn- time-format []
  (time-format/formatter "yyyy-MM-dd HH:mm" (time/default-time-zone)))

(defn- time-format-with-seconds []
  (time-format/formatter "yyyy-MM-dd HH:mm:ss" (time/default-time-zone)))

(defn localize-time [time]
  #?(:clj (when time
            (let [time (if (string? time) (time-format/parse time) time)]
              (time-format/unparse (time-format) time)))
     :cljs (let [time (if (string? time) (time-format/parse time) time)]
             (when time
               (time-format/unparse-local (time-format) (time/to-default-time-zone time))))))

(defn localize-time-with-seconds
  "Localized datetime with second precision."
  [time]
  #?(:clj (when time
            (let [time (if (string? time) (time-format/parse time) time)]
              (time-format/unparse (time-format-with-seconds) time)))
     :cljs (let [time (if (string? time) (time-format/parse time) time)]
             (when time
               (time-format/unparse-local (time-format-with-seconds) (time/to-default-time-zone time))))))

(defn localize-utc-date
  "For a given time instant, return the ISO date (yyyy-MM-dd) that it corresponds to in UTC."
  [time]
  #?(:clj (time-format/unparse (time-format/formatter "yyyy-MM-dd") time)
     :cljs (time-format/unparse (time-format/formatter "yyyy-MM-dd") (time-coerce/to-local-date time))))

(defn format-utc-datetime
  "For a given time instant, format it in UTC."
  [time]
  (time-format/unparse (time-format/formatters :date-time) time))

(deftest test-localize-utc-date []
  (is (= "2020-09-29" (localize-utc-date (time/date-time 2020 9 29 1 1))))
  (is (= "2020-09-29" (localize-utc-date (time/date-time 2020 9 29 23 59))))
  ;; [cl]js dates are always in UTC, so we can only test these for clj
  #?(:clj (do
            (is (= "2020-09-29" (localize-utc-date (time/to-time-zone (time/date-time 2020 9 29 23 59)
                                                                      (time/time-zone-for-offset 5)))))
            (is (= "2020-09-29" (localize-utc-date (time/to-time-zone (time/date-time 2020 9 29 1 1)
                                                                      (time/time-zone-for-offset -5))))))))


(def ^:private event-types
  {:application.event/applicant-changed :t.applications.events/applicant-changed
   :application.event/approved :t.applications.events/approved
   :application.event/attachments-redacted :t.applications.events/attachments-redacted
   :application.event/closed :t.applications.events/closed
   :application.event/review-requested :t.applications.events/review-requested
   :application.event/reviewed :t.applications.events/reviewed
   :application.event/copied-from :t.applications.events/copied-from
   :application.event/copied-to :t.applications.events/copied-to
   :application.event/created :t.applications.events/created
   :application.event/decided :t.applications.events/decided
   :application.event/decider-invited :t.applications.events/decider-invited
   :application.event/decider-joined :t.applications.events/decider-joined
   :application.event/decision-requested :t.applications.events/decision-requested
   :application.event/deleted :t.applications.events/deleted
   :application.event/draft-saved :t.applications.events/draft-saved
   :application.event/external-id-assigned :t.applications.events/external-id-assigned
   :application.event/expiration-notifications-sent :t.applications.events/expiration-notifications-sent
   :application.event/licenses-accepted :t.applications.events/licenses-accepted
   :application.event/licenses-added :t.applications.events/licenses-added
   :application.event/member-added :t.applications.events/member-added
   :application.event/member-invited :t.applications.events/member-invited
   :application.event/member-joined :t.applications.events/member-joined
   :application.event/member-removed :t.applications.events/member-removed
   :application.event/member-uninvited :t.applications.events/member-uninvited
   :application.event/rejected :t.applications.events/rejected
   :application.event/remarked :t.applications.events/remarked
   :application.event/resources-changed :t.applications.events/resources-changed
   :application.event/returned :t.applications.events/returned
   :application.event/revoked :t.applications.events/revoked
   :application.event/reviewer-invited :t.applications.events/reviewer-invited
   :application.event/reviewer-joined :t.applications.events/reviewer-joined
   :application.event/submitted :t.applications.events/submitted})

(defn localize-decision [event]
  (when-let [decision (:application/decision event)]
    (text-format
     (case decision
       :approved :t.applications.events/approved
       :rejected :t.applications.events/rejected
       :t.applications.events/unknown)
     (application-util/get-member-name (:event/actor-attributes event)))))

(defn localize-invitation [{:keys [name email]}]
  (str name " <" email ">"))

(defn localize-event [event]
  (let [event-type (:event/type event)]
    (str
     (text-format
      (get event-types event-type :t.applications.events/unknown)
      (application-util/get-member-name (:event/actor-attributes event))
      (case event-type
        :application.event/review-requested
        (str/join ", " (mapv application-util/get-member-name
                             (:application/reviewers event)))

        :application.event/decision-requested
        (str/join ", " (mapv application-util/get-member-name
                             (:application/deciders event)))

        :application.event/created
        (:application/external-id event)

        :application.event/external-id-assigned
        (:application/external-id event)

        (:application.event/member-added
         :application.event/member-removed)
        (application-util/get-member-name (:application/member event))

        :application.event/applicant-changed
        (application-util/get-member-name (:application/applicant event))

        (:application.event/member-invited
         :application.event/member-uninvited)
        (localize-invitation (:application/member event))

        :application.event/decider-invited
        (localize-invitation (:application/decider event))

        :application.event/reviewer-invited
        (localize-invitation (:application/reviewer event))

        :application.event/resources-changed
        (str/join ", " (mapv #(localized (:catalogue-item/title %))
                             (:application/resources event)))

        :application.event/attachments-redacted
        (when (seq (:event/attachments event))
          (text :t.applications/redacted-attachments-replaced))

        nil))
     (case event-type
       :application.event/approved
       (when-let [end (:entitlement/end event)]
         (str " " (text-format :t.applications/entitlement-end (localize-utc-date end))))

       nil))))

(defn localize-attachment
  "If attachment is redacted, return localized text for redacted attachment.
   Otherwise return value of :attachment/filename."
  [attachment]
  (if (= :filename/redacted (:attachment/filename attachment))
    (text :t.applications/attachment-filename-redacted)
    (:attachment/filename attachment)))
