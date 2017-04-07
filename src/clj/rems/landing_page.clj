(ns rems.landing-page
  (:require [ring.util.response :refer [redirect]]
            [compojure.core :refer [defroutes GET]]
            [rems.role-switcher :refer [has-roles?]]
            [rems.context :as context]))

(defn- landing-page [req]
  (if (has-roles? :approver)
    (redirect "/approvals")
    (redirect "/catalogue")))

(defroutes landing-page-routes
  (GET "/landing_page" req (landing-page req)))
