(ns rems.poller.email
  "Sending emails based on application events."
  (:require [rems.json :as json]
            [rems.db.applications :as applications]
            [rems.db.core :as db]))

;;; Mapping events to emails

;; TODO pass in application state
(defmulti ^:private event-to-emails-impl
  (fn [event _application] (:event/type event)))

(defmethod event-to-emails-impl :default [_event _application]
  [])

(defmethod event-to-emails-impl :application.event/approved [event application]
  ;; TODO other members
  [{:to (:applicantuserid application)
    :body (str "application " (:application/id event) " has been approved")}])

(defmethod event-to-emails-impl :application.event/rejected [event application]
  ;; TODO other members
  [{:to (:applicantuserid application)
    :body (str "application " (:application/id event) " has been rejected")}])

(defmethod event-to-emails-impl :application.event/comment-requested [event _application]
  (for [c (:application/commenters event)]
    {:to c
     :body (str "please comment on " (:application/id event))}))

(defmethod event-to-emails-impl :application.event/decision-requested [event _application]
  (for [c (:application/deciders event)]
    {:to c
     :body (str "please decide " (:application/id event))}))

(defmethod event-to-emails-impl :application.event/commented [event application]
  (for [h (get-in application [:workflow :handlers])]
    {:to h
     :body (str "comment by " (:event/actor event)  ": " (:application/comment event))}))

(defmethod event-to-emails-impl :application.event/decided [event application]
  (for [h (get-in application [:workflow :handlers])]
    {:to h
     :body (str "decision by " (:event/actor event)  ": " (:application/decision event))}))

(defmethod event-to-emails-impl :application.event/member-added [event _application]
  [{:to (:userid (:application/member event))
    :body "you've been added"}])

(defmethod event-to-emails-impl :application.event/member-invited [event _application]
  [{:to (:email (:application/member event))
    :body "invitation email"}])

(defn event-to-emails [event]
  (when-let [app-id (:application/id event)]
    ;; TODO use api-get-application-v2 or similar
    (event-to-emails-impl event (applications/get-application-state app-id))))

;;; Poller

(defn get-state []
  (or (json/parse-string (:state (db/get-poller-state {:name (name ::state)})))
      {:event/id -1}))

(defn set-state! [state]
  (db/set-poller-state! {:name (name ::state) :state (json/generate-string state)})
  nil)

(defn run []
  (let [prev-state (get-state)
        events (applications/get-dynamic-application-events-since (:event/id prev-state))]
    (prn :START prev-state)
    (when-not (empty? events)
      (doseq [e events]
        (prn (event-to-emails e)))
      (set-state! {:event/id (:event/id (last events))}))))
