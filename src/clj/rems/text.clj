(ns rems.text
  (:require [clj-time.core :as time]
            [clj-time.format :as format]
            [rems.context :as context]))

(defn text
  "Return the tempura translation for a given key. Additional fallback
  keys can be given."
  [& ks]
  (context/*tempura* (conj (vec ks) :t/missing)))

(defn text-format
  "Return the tempura translation for a given key & format arguments"
  [k & args]
  (context/*tempura* [k :t/missing] (vec args)))

(defn localize-state [state]
  (case state
    "draft" :t.applications.states/draft
    "applied" :t.applications.states/applied
    "approved" :t.applications.states/approved
    "rejected" :t.applications.states/rejected
    "returned" :t.applications.states/returned
    "withdrawn" :t.applications.states/withdrawn
    "closed" :t.applications.states/closed
    :t.applications.states/unknown))

(defn localize-event [event]
  (case event
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
    :t.applications.events/unknown))

(def ^:private time-format (format/formatter "yyyy-MM-dd HH:mm"
                                             (time/default-time-zone)))

(defn localize-time [time]
  (format/unparse time-format time))
