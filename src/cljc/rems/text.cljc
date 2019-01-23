(ns rems.text
  #?(:clj (:require [clj-time.core :as time]
                    [clj-time.format :as format]
                    [rems.context :as context]
                    [rems.locales :as locales]
                    [taoensso.tempura :refer [tr]])
     :cljs (:require [cljs-time.core :as time]
                     [cljs-time.format :as format]
                     [re-frame.core :as rf]
                     [taoensso.tempura :refer [tr]])))

(defn with-language [lang f]
  #?(:clj (binding [context/*lang* lang
                    context/*tempura* (partial tr (locales/tempura-config) [lang])]
            (f))))

(defn text-format
  "Return the tempura translation for a given key & format arguments"
  [k & args]
  #?(:clj (context/*tempura* [k :t/missing] (vec args))
     :cljs (let [translations (rf/subscribe [:translations])
                 language (rf/subscribe [:language])]
             (tr {:dict @translations}
                 [@language]
                 [k :t/missing]
                 (vec args)))))

(defn text
  "Return the tempura translation for a given key. Additional fallback
  keys can be given."
  [& ks]
  #?(:clj (context/*tempura* (conj (vec ks) (text-format :t.missing (vec ks))))
     :cljs (let [translations (rf/subscribe [:translations])
                 language (rf/subscribe [:language])]
             (try
               (tr {:dict @translations}
                   [@language]
                   (conj (vec ks) (text-format :t.missing (vec ks))))
               (catch js/Object e
                 ;; fail gracefully if the re-frame state is incomplete
                 (.error js/console e)
                 (str (vec ks)))))))

(defn localize-state [state]
  (text (case state
          "draft" :t.applications.states/draft
          "applied" :t.applications.states/applied
          "approved" :t.applications.states/approved
          "rejected" :t.applications.states/rejected
          "returned" :t.applications.states/returned
          "withdrawn" :t.applications.states/withdrawn
          "closed" :t.applications.states/closed
          :rems.workflow.dynamic/draft :t.applications.dynamic-states/draft
          :rems.workflow.dynamic/submitted :t.applications.dynamic-states/submitted
          :rems.workflow.dynamic/approved :t.applications.dynamic-states/approved
          :rems.workflow.dynamic/rejected :t.applications.dynamic-states/rejected
          :rems.workflow.dynamic/closed :t.applications.dynamic-states/closed
          :rems.workflow.dynamic/returned :t.applications.dynamic-states/returned
          :t.applications.states/unknown)))

(defn localize-event [event]
  (text (case event
          ;; static
          "add-member" :t.application.events/add-member
          "apply" :t.applications.events/apply
          "approve" :t.applications.events/approve
          "autoapprove" :t.applications.events/autoapprove
          "close" :t.applications.events/close
          "reject" :t.applications.events/reject
          "return" :t.applications.events/return
          "review" :t.applications.events/review
          "review-request" :t.applications.events/review-request
          "save" :t.applications.events/save
          "third-party-review" :t.applications.events/third-party-review
          "withdraw" :t.applications.events/withdraw

          ;; dynamic
          "submitted" :t.applications.dynamic-events/submitted
          "returned" :t.applications.dynamic-events/returned
          "comment-requested" :t.applications.dynamic-events/comment-requested
          "commented" :t.applications.dynamic-events/commented
          "decision-requested" :t.applications.dynamic-events/decision-requested
          "decided" :t.applications.dynamic-events/decided
          "approved" :t.applications.dynamic-events/approved
          "rejected" :t.applications.dynamic-events/rejected
          "closed" :t.applications.dynamic-events/closed
          "member-added" :t.applications.dynamic-events/member-added

          :t.applications.events/unknown)))

(defn localize-decision [decision]
  (text (case decision
          :approved :t.applications.dynamic-events/approved
          :rejected :t.applications.dynamic-events/rejected
          :t.applications.events/unknown)))

(def ^:private time-format
  (format/formatter "yyyy-MM-dd HH:mm" (time/default-time-zone)))

(defn localize-time [time]
  #?(:clj (format/unparse time-format time)
     :cljs (let [time (if (string? time) (format/parse time) time)]
             (when time
               (format/unparse-local time-format (time/to-default-time-zone time))))))

(defn localize-item
  ([item]
   #?(:cljs (localize-item item @(rf/subscribe [:language]))))
  ([item language]
   #?(:cljs (merge item (get-in item [:localizations language])))))
