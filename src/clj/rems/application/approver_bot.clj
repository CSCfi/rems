(ns rems.application.approver-bot
  (:require [clj-time.core :as time]
            [rems.application-util :as application-util]))

(def bot-userid "approver-bot")

(defn- handler? [app userid]
  (contains? (->> (get-in app [:application/workflow :workflow.dynamic/handlers])
                  (mapv :userid)
                  set)
             userid))

(defn- any-member-blacklisted? [app {:keys [blacklisted?]}]
  (some (fn [[user resource]]
          (blacklisted? user resource))
        (for [member (application-util/applicant-and-members app)
              resource (:application/resources app)]
          [(:userid member) (:resource/ext-id resource)])))

(defn generate-commands [app injections]
  (when (and (handler? app bot-userid)
             (= :application.state/submitted (:application/state app))
             (not (any-member-blacklisted? app injections)))
    [{:type :application.command/approve
      :actor bot-userid
      :time (time/now)
      :application-id (:application/id app)
      :comment ""}]))
