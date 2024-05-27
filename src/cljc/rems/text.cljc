(ns rems.text
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [rems.common.application-util :as application-util]
            [rems.common.util :refer [not-blank]]
            [rems.tempura]
            #?@(:clj
                [[clj-time.core :as time]
                 [clj-time.format :as time-format]
                 [rems.locales]
                 [rems.context :as context]]
                :cljs
                [[cljs-time.core :as time]
                 [cljs-time.format :as time-format]
                 [cljs-time.coerce :as time-coerce]
                 [rems.config]
                 [rems.globals]])))

#?(:clj
   (defmacro with-language [lang & body]
     `(let [lang# ~lang] ; ensure lang is evaluated only once
        (binding [rems.context/*lang* lang#]
          (assert (keyword? lang#) {:lang lang#})
          ~@body))))

(defn- failsafe-fallback
  "Fallback for when loading the translations has failed."
  [k args]
  (pr-str (vec (if (= :t/missing k)
                 (first args)
                 (cons k args)))))

(def cached-tr (atom nil))
(defn reset-cached-tr! [] (reset! cached-tr nil))

(defn ensure-cached-tr! []
  (when-not (fn? @cached-tr)
    (reset! cached-tr (rems.tempura/get-cached-tr #?(:clj rems.locales/translations
                                                     :cljs @rems.globals/translations))))
  @cached-tr)

(defn- tr [ks & [args]]
  (let [language #?(:clj context/*lang*
                    :cljs @rems.config/current-language)]
    ((ensure-cached-tr!) [language]
                         (vec ks)
                         (some-> args vec))))

(defn text-format
  "Return the tempura translation for a given key and arguments:

   `(text-format :key 1 2)`"
  [k & args]
  #?(:clj (tr [k :t/missing]
              args)
     :cljs (tr [k :t/missing (failsafe-fallback k args)]
               args)))

(defn text-format-map
  "Return the tempura translation for a given key and argument map:

   `(text-format-map :key {:a 1 :b 2})`

   Additional vector of keys can be given to create vector arguments from map,
   in which case resource compiler infers (from resource) which parameters to use:

   `(text-format-map :key {:a 1 :b 2} [:b :a])`"
  ([k arg-map] (text-format k arg-map))
  ([k arg-map arg-vec] (apply text-format k arg-map (mapv arg-map arg-vec))))

(defn text-no-fallback
  "Return the tempura translation for a given key. Additional fallback
  keys can be given but there is no default fallback text."
  [& ks]
  #?(:clj (tr ks)
     :cljs (try
             (tr ks)
             (catch js/Object e
               ;; fail gracefully if the re-frame state is incomplete
               (.error js/console e)
               (str (vec ks))))))

(defn text
  "Return the tempura translation for a given key. Additional fallback
  keys can be given."
  [& ks]
  #?(:clj (or (tr ks) ; optimization: fetch missing translation only when needed
              (text-format :t/missing ks))
     ;; NB: we can't call the text-no-fallback here as in CLJS
     ;; we can both call this as function or use as a React component
     :cljs (try
             (or (tr ks)
                 (text-format :t/missing ks))
             (catch js/Object e
               ;; fail gracefully if the re-frame state is incomplete
               (.error js/console e)
               (str (vec ks))))))

(defn localized [m]
  (let [lang #?(:clj context/*lang*
                :cljs @rems.config/current-language)]
    (or (get m lang)
        (first (vals m)))))

;; TODO: replace usages of `get-localized-title` with `localized`
(defn get-localized-title [item]
  (let [lang #?(:clj context/*lang*
                :cljs @rems.config/current-language)]
    (or (get-in item [:localizations lang :title])
        (:title (first (vals (get item :localizations)))))))

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

(defn localize-processing-states [application]
  (let [states (:application/processing-state application)
        public-state (some-> states :public :processing-state/title localized)
        private-state (some-> states :private :processing-state/title localized)]
    (str/join ", "
              (remove str/blank? [public-state private-state]))))

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

(defn time-format []
  (time-format/formatter "yyyy-MM-dd HH:mm" (time/default-time-zone)))

(defn time-format-with-seconds []
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
   :application.event/processing-state-changed :t.applications.events/processing-state-changed
   :application.event/rejected :t.applications.events/rejected
   :application.event/remarked :t.applications.events/remarked
   :application.event/resources-changed :t.applications.events/resources-changed
   :application.event/returned :t.applications.events/returned
   :application.event/review-requested :t.applications.events/review-requested
   :application.event/reviewed :t.applications.events/reviewed
   :application.event/reviewer-invited :t.applications.events/reviewer-invited
   :application.event/reviewer-joined :t.applications.events/reviewer-joined
   :application.event/revoked :t.applications.events/revoked
   :application.event/submitted :t.applications.events/submitted
   :application.event/voted :t.applications.events/voted})

(defn localize-user
  "Returns localization for special user if possible. Otherwise returns formatted user."
  [user]
  (case (:userid user)
    "rems-handler" (text :t.roles/anonymous-handler)
    (application-util/get-member-name user)))

(defn localize-decision [event]
  (when-let [decision (:application/decision event)]
    (text-format-map
     (case decision
       :approved :t.applications.events/approved
       :rejected :t.applications.events/rejected)
     {:event-actor (localize-user (:event/actor-attributes event))}
     [:event-actor])))

(defn localize-invitation [{:keys [name email]}]
  (str name " <" email ">"))

(defn- get-event-params [event]
  (case (:event/type event)
    :application.event/applicant-changed        {:new-applicant (localize-user (:application/applicant event))}
    :application.event/created                  {:application-external-id (:application/external-id event)}
    :application.event/decider-invited          {:invited-user (localize-invitation (:application/decider event))}
    :application.event/decision-requested       {:requested-users (->> (:application/deciders event)
                                                                       (map localize-user)
                                                                       (str/join ", "))}
    :application.event/external-id-assigned     {:application-external-id (:application/external-id event)}
    :application.event/member-added             {:added-user (localize-user (:application/member event))}
    :application.event/member-invited           {:invited-user (localize-invitation (:application/member event))}
    :application.event/member-removed           {:removed-user (localize-user (:application/member event))}
    :application.event/member-uninvited         {:uninvited-user (localize-invitation (:application/member event))}
    :application.event/processing-state-changed {:state (localized (get-in event [:application/processing-state :processing-state/title]))}
    :application.event/resources-changed        {:catalogue-items (->> (:application/resources event)
                                                                       (map (comp localized :catalogue-item/title))
                                                                       (str/join ", "))}
    :application.event/reviewer-invited         {:invited-user (localize-invitation (:application/reviewer event))}
    :application.event/review-requested         {:requested-users (->> (:application/reviewers event)
                                                                       (map localize-user)
                                                                       (str/join ", "))}
    :application.event/voted                    {:vote (when-some [vote (not-blank (:vote/value event))]
                                                         (text (keyword (str "t" ".applications.voting.votes") vote)))}
    nil))

(defn- localize-event-extras [localized-event event]
  (let [localized-extras (case (:event/type event)
                           :application.event/approved             (when-let [end (:entitlement/end event)]
                                                                     (str localized-event " " (text-format :t.applications/entitlement-end (localize-utc-date end))))
                           :application.event/attachments-redacted (when (seq (:event/attachments event))
                                                                     (str localized-event " " (text :t.applications/redacted-attachments-replaced)))
                           nil)]
    (or localized-extras
        localized-event)))

(defn localize-event [event]
  (let [event-type (:event/type event)
        event-localization-key (get event-types event-type :t.applications.events/unknown)
        event-actor (localize-user (:event/actor-attributes event))
        params (get-event-params event)
        vec-params (sort (keys params)) ; ensure some default order
        ]
    (-> (text-format-map event-localization-key
                         (merge {:event-actor event-actor} params)
                         (vec (cons :event-actor vec-params)))
        (as-> localized-event ; XXX: helper for translations with conditional parts
              (localize-event-extras localized-event event)))))

(defn localize-attachment
  "If attachment is redacted, return localized text for redacted attachment.
   Otherwise return value of :attachment/filename."
  [attachment]
  (let [filename (:attachment/filename attachment)]
    (cond
      (= :filename/redacted filename)
      (text :t.applications/attachment-filename-redacted)

      (:attachment/redacted attachment)
      (text-format :t.label/parens filename (text :t.applications/attachment-filename-redacted))

      :else filename)))

(def ^:private localized-roles
  {;; :api-key
   :applicant :t.roles/applicant
   :decider :t.roles/decider
   ;; :everyone-else
   ;; :expirer
   :handler :t.roles/handler
   ;; :logged-in
   :member :t.roles/member
   ;; :organization-owner
   ;; :owner
   :past-decider :t.roles/past-decider
   :past-reviewer :t.roles/past-reviewer
   ;; :reporter
   :reviewer :t.roles/reviewer
   ;; :user-owner
   })

(defn localize-role [role]
  (text (get localized-roles role) :t/unknown))

(def ^:private localized-commands
  {:application.command/accept-invitation :t.commands/accept-invitation
   :application.command/accept-licenses :t.commands/accept-licenses
   :application.command/add-licenses :t.commands/add-licenses
   :application.command/add-member :t.commands/add-member
   :application.command/approve :t.commands/approve
   :application.command/assign-external-id :t.commands/assign-external-id
   :application.command/change-applicant :t.commands/change-applicant
   :application.command/change-processing-state :t.commands/change-processing-state
   :application.command/change-resources :t.commands/change-resources
   :application.command/close :t.commands/close
   :application.command/copy-as-new :t.commands/copy-as-new
   ;; :application.command/create
   :application.command/decide :t.commands/decide
   :application.command/delete :t.commands/delete
   :application.command/invite-decider :t.commands/invite-decider
   :application.command/invite-member :t.commands/invite-member
   :application.command/invite-reviewer :t.commands/invite-reviewer
   :application.command/redact-attachments :t.commands/redact-attachments
   :application.command/reject :t.commands/reject
   :application.command/remark :t.commands/remark
   :application.command/remove-member :t.commands/remove-member
   :application.command/request-decision :t.commands/request-decision
   :application.command/request-review :t.commands/request-review
   :application.command/return :t.commands/return
   :application.command/review :t.commands/review
   :application.command/revoke :t.commands/revoke
   :application.command/save-draft :t.commands/save-draft
   ;; :application.command/send-expiration-notifications
   :application.command/submit :t.commands/submit
   :application.command/uninvite-member :t.commands/uninvite-member
   :application.command/vote :t.commands/vote})

(defn localize-command [command]
  (let [command-type (if (keyword? command)
                       command
                       (:type command))]
    (text (get localized-commands command-type) :t/unknown)))
