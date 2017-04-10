(ns rems.landing-page
  (:require [ring.util.response :refer [redirect]]
            [compojure.core :refer [defroutes GET]]
            [rems.role-switcher :refer [has-roles?]]
            [rems.context :as context]
            [rems.db.users :as users]
            [rems.util :refer [get-user-id]]))

(defn- landing-page [req]
  (users/add-user! (get-user-id) context/*user*)
  (if (has-roles? :approver)
    (redirect "/approvals")
    (redirect "/catalogue")))

(defroutes landing-page-routes
  (GET "/landing_page" req (landing-page req)))
