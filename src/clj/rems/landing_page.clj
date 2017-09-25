(ns rems.landing-page
  (:require [compojure.core :refer [GET defroutes]]
            [rems.context :as context]
            [rems.db.users :as users]
            [rems.roles :refer [has-roles?]]
            [rems.util :refer [get-user-id]]
            [ring.util.response :refer [redirect]]))

(defn- landing-page [req]
  (users/add-user! (get-user-id) context/*user*)
  (cond
    (has-roles? :approver) (redirect "/actions")
    (has-roles? :reviewer) (redirect "/actions")
    :else (redirect "/catalogue")))

(defroutes landing-page-routes
  (GET "/landing_page" req (landing-page req)))
