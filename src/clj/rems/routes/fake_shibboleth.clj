(ns rems.routes.fake-shibboleth
  (:require [rems.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [ring.util.response :refer [response redirect]]
            [buddy.auth.backends.session :refer [session-backend]]
            [clojure.java.io :as io]))

(defn- fake-login [{session :session}]
      (assoc (redirect "/catalogue")
             :session (assoc session :identity "developer")))

(defn- fake-logout [{session :session}]
  (-> (redirect "/")
      (assoc :session (dissoc session :identity))))

(defroutes fake-shibboleth-routes
  (GET "/Shibboleth.sso/Login" req (fake-login req))
  (GET "/logout" req (fake-logout req)))
