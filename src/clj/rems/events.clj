(ns rems.events
  "Components and endpoints for submitting events (review, approve, etc.) for applications"
  (:require [compojure.core :refer [POST defroutes]]
            [rems.db.applications :as applications]
            [rems.roles :refer [has-roles?]]
            [rems.text :refer [text]]
            [rems.util :refer :all]
            [ring.util.response :refer [redirect]]))

;; TODO handle closing when no draft or anything saved yet
(defroutes events-routes
  (POST "/event/:id/:round" [id round :as request]
        (let [id (Long/parseLong id)
              round (Long/parseLong round)
              input (:form-params request)
              action (cond (get input "approve") :approve
                           (get input "reject") :reject
                           (get input "return") :return
                           (get input "review") :review
                           (get input "review-request") :review-request
                           (get input "withdraw") :withdraw
                           (get input "close") :close
                           (get input "third-party-review") :third-party-review
                           :else (errorf "Unknown action!"))
              comment (get input "comment")
              comment (when-not (empty? comment) comment)]
          (case action
            :approve (applications/approve-application id round comment)
            :reject (applications/reject-application id round comment)
            :return (applications/return-application id round comment)
            :review (applications/review-application id round comment)
            :review-request (applications/send-review-request id round comment (get input "recipients"))
            :withdraw (applications/withdraw-application id round comment)
            :close (applications/close-application id round comment)
            :third-party-review (applications/perform-third-party-review id round comment))
          (assoc (redirect (if (or (has-roles? :approver) (has-roles? :reviewer)) "/actions" "/applications") :see-other)
                 :flash [{:status :success
                          :contents (case action
                                      :approve (text :t.actions/approve-success)
                                      :reject (text :t.actions/reject-success)
                                      :return (text :t.actions/return-success)
                                      :review (text :t.actions/review-success)
                                      :review-request (text :t.actions/review-request-success)
                                      :withdraw (text :t.actions/withdraw-success)
                                      :close (text :t.actions/close-success)
                                      :third-party-review (text :t.actions/review-success))}]))))
