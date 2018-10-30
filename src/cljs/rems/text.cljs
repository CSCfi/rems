(ns rems.text
  (:require [cljs-time.core :as time]
            [cljs-time.format :as format]
            [re-frame.core :as rf]
            [taoensso.tempura :as tempura :refer [tr]]))

(defn text
  "Return the tempura translation for a given key. Additional fallback
  keys can be given."
  [& ks]
  (let [translations (rf/subscribe [:translations])
        language (rf/subscribe [:language])]
    (tr {:dict @translations}
        [@language]
        (conj (vec ks) :t/missing))))

(defn text-format
  "Return the tempura translation for a given key & format arguments"
  [k & args]
  (let [translations (rf/subscribe [:translations])
        language (rf/subscribe [:language])]
    (tr {:dict @translations}
        [@language]
        [k :t/missing]
        (vec args))))

(defn localize-state [state]
  (text (case state
          "draft" :t.applications.states/draft
          "applied" :t.applications.states/applied
          "approved" :t.applications.states/approved
          "rejected" :t.applications.states/rejected
          "returned" :t.applications.states/returned
          "withdrawn" :t.applications.states/withdrawn
          "closed" :t.applications.states/closed
          :t.applications.states/unknown)))

(defn localize-event [event]
  (text (case event
          "apply" :t.applications.events/apply
          "approve" :t.applications.events/approve
          "autoapprove" :t.applications.events/autoapprove
          "close" :t.applications.events/close
          "reject" :t.applications.events/reject
          "return" :t.applications.events/return
          "review" :t.applications.events/review
          "review-request" :t.applications.events/review-request
          "withdraw" :t.applications.events/withdraw
          "third-party-review" :t.applications.events/third-party-review
          :t.applications.events/unknown)))

(def ^:private time-format
  (format/formatter "yyyy-MM-dd HH:mm" (time/default-time-zone)))

(defn localize-time [time]
  (when time
    (let [time (or (when (string? time)
                     (format/parse time))
                   time)]
      (format/unparse-local time-format (time/to-default-time-zone time)))))

(defn localize-item
  ([item]
   (localize-item item @(rf/subscribe [:language])))
  ([item language]
   (merge item (get-in item [:localizations language]))))
