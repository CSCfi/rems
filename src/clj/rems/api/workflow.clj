(ns rems.api.workflow
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [check-user check-roles]]
            [rems.db.core :as db]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def Actor
  {:actoruserid s/Str
   :round s/Num
   :role (s/enum "approver" "reviewer")})

(def Workflow
  {:id s/Num
   :owneruserid s/Str
   :modifieruserid s/Str
   :title s/Str
   :final-round s/Num
   :start DateTime
   :end (s/maybe DateTime)
   :actors [Actor]})

(defn- format-workflow
  [{:keys [id owneruserid modifieruserid title fnlround start endt]}]
  {:id id
   :owneruserid owneruserid
   :modifieruserid modifieruserid
   :title title
   :final-round fnlround
   :start start
   :end endt})

(defn- get-workflows []
  (doall
   (for [wf (db/get-workflows)]
     (assoc (format-workflow wf)
            :actors (db/get-workflow-actors {:wfid (:id wf)})))))

(def workflow-api
  (context "/workflow" []
    :tags ["workflow"]

    (GET "/" []
      :summary "Get workflows"
      :return [Workflow]
      (check-user)
      (check-roles :owner)
      (ok (get-workflows)))))
