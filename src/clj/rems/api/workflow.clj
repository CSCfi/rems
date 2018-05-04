(ns rems.api.workflow
  (:require [compojure.api.sweet :refer :all]
            #_[rems.api.schema :refer :all]
            [rems.api.util :refer [check-user]]
            [rems.db.core :as db]
            [rems.util :refer [get-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def Actor
  {:actoruserid s/Str
   :id s/Any ;; TODO join by app shouldn't be done
   :round s/Num
   :role (s/enum "approver" "reviewer")})

(def Workflow
  {:id s/Num
   :owneruserid s/Str
   :modifieruserid s/Str
   :title s/Str
   :fnlround s/Num
   :visibility s/Str
   :start DateTime
   :endt (s/maybe DateTime)
   :actors [Actor]})

(defn get-workflows []
  (doall
   (for [wf (db/get-workflows)]
     (assoc wf :actors (db/get-workflow-actors {:wfid (:id wf)})))))

(def workflow-api
  (context "/workflow" []
    :tags ["workflow"]

    (GET "/" []
      :summary "Get workflows"
      :return [Workflow]
      (check-user)
      (ok (get-workflows)))))
