(ns rems.service.todos
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is]]
            [rems.application.commands :as commands]
            [rems.db.applications :as applications]))

(def ^:private todo-roles
  #{:handler :reviewer :decider :past-reviewer :past-decider})

(defn- potential-todo? [application]
  (and (some todo-roles (:application/roles application))
       (not= :application.state/draft (:application/state application))))

(defn- get-potential-todos [user-id]
  (->> (applications/get-all-applications user-id)
       (filter potential-todo?)))

(def ^:private todo-commands
  #{:application.command/approve
    :application.command/reject
    :application.command/revoke ; should not be available in submitted state, but let's keep it here just in case
    :application.command/return
    :application.command/close ; available also in other states than submitted, but that's okay
    :application.command/request-review
    :application.command/review
    :application.command/request-decision
    :application.command/decide})

(defn- todo? [application]
  (and (= :application.state/submitted (:application/state application))
       (some todo-commands (:application/permissions application))))

(deftest test-todo-commands
  (let [non-todo-commands
        #{;; always available
          :application.command/accept-invitation
          ;; only done by the applicant or members
          :application.command/accept-licenses
          :application.command/copy-as-new
          :application.command/create
          :application.command/delete
          :application.command/save-draft
          :application.command/submit
          ;; will not change the application's state, so they
          ;; can be ignored from a workflow point of view
          :application.command/add-licenses
          :application.command/add-member
          :application.command/assign-external-id
          :application.command/change-applicant
          :application.command/change-resources
          :application.command/invite-member
          :application.command/invite-reviewer
          :application.command/invite-decider
          :application.command/remove-member
          :application.command/uninvite-member
          ;; remarks can be made without a request, also on handled todos
          :application.command/remark
          :application.command/send-expiration-notifications
          :application.command/redact-attachments}
        all-commands (set (keys commands/command-schemas))]

    ;; This test is to make sure that as new commands are added,
    ;; we will make a conscious decision whether a submitted application
    ;; with that permission should be shown on the Actions page as
    ;; an "open application" or a "processed application".
    (is (= (set/difference all-commands non-todo-commands)
           todo-commands)
        "seems like a new command has been added; is it a todo or handled todo?")))

(defn get-todos [user-id]
  (->> (get-potential-todos user-id)
       (filter todo?)))

(defn get-handled-todos [user-id]
  (->> (get-potential-todos user-id)
       (remove todo?)))
