(ns rems.text
  (:require #?(:clj [clj-time.core :as time]
               :cljs [cljs-time.core :as time])
            #?(:clj [clj-time.format :as format]
               :cljs [cljs-time.format :as format])
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
  "Return the tempura translation for a given key & format arguments"
  [k & args]
  #?(:clj (context/*tempura* [k :t/missing] (vec args))
     :cljs (let [translations (rf/subscribe [:translations])
                 language (rf/subscribe [:language])]
             (tr {:dict @translations}
                 [@language]
                 [k :t/missing (failsafe-fallback k args)]
                 (vec args)))))

(defn text
  "Return the tempura translation for a given key. Additional fallback
  keys can be given."
  [& ks]
  #?(:clj (context/*tempura* (conj (vec ks) (text-format :t/missing (vec ks))))
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

(def ^:private event-types
  {:application.event/approved :t.applications.events/approved
   :application.event/closed :t.applications.events/closed
   :application.event/review-requested :t.applications.events/review-requested
   :application.event/reviewed :t.applications.events/reviewed
   :application.event/copied-from :t.applications.events/copied-from
   :application.event/copied-to :t.applications.events/copied-to
   :application.event/created :t.applications.events/created
   :application.event/decided :t.applications.events/decided
   :application.event/decision-requested :t.applications.events/decision-requested
   :application.event/draft-saved :t.applications.events/draft-saved
   :application.event/external-id-assigned :t.applications.events/external-id-assigned
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
   :application.event/submitted :t.applications.events/submitted})

(defn localize-decision [event]
  (let [decision (:application/decision event)]
    (text-format
     (case decision
       :approved :t.applications.events/approved
       :rejected :t.applications.events/rejected
       :t.applications.events/unknown)
     (application-util/get-member-name (:event/actor-attributes event)))))

(defn localize-event [event]
  (let [event-type (:event/type event)]
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

       :application.event/external-id-assigned
       (:application/external-id event)

       (:application.event/member-added
        :application.event/member-invited
        :application.event/member-removed
        :application.event/member-uninvited)
       (application-util/get-member-name (:application/member event))

       :application.event/resources-changed
       (str/join ", " (mapv #(localized (:catalogue-item/title %))
                            (:application/resources event)))

       nil))))

(defn- time-format []
  (format/formatter "yyyy-MM-dd HH:mm" (time/default-time-zone)))

(defn localize-time [time]
  #?(:clj (when time
            (let [time (if (string? time) (format/parse time) time)]
              (format/unparse (time-format) time)))
     :cljs (let [time (if (string? time) (format/parse time) time)]
             (when time
               (format/unparse-local (time-format) (time/to-default-time-zone time))))))
