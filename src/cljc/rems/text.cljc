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
             (tr {:dict @translations}
                 [@language]
                 (conj (vec ks) (text-format :t.missing (vec ks)))))))

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
          "add-member" :t.application.events/add-member
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
  #?(:clj (format/unparse time-format time)
     :cljs (let [time (if (string? time) (format/parse time) time)]
             (when time
               (format/unparse-local time-format (time/to-default-time-zone time))))))

(defn localize-item
  ([item]
   #?(:cljs (localize-item item @(rf/subscribe [:language]))))
  ([item language]
   #?(:cljs (merge item (get-in item [:localizations language])))))
