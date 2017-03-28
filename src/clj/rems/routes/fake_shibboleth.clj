(ns rems.routes.fake-shibboleth
  (:require [rems.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [ring.util.response :refer [response redirect content-type]]
            [hiccup.util :refer [url]]
            [buddy.auth.backends.session :refer [session-backend]]
            [hiccup.page :refer [html5]]
            [clojure.java.io :as io]))

(defn- fake-login [session username]
  (assoc (redirect "/catalogue")
         :session (assoc session :identity username)))

(defn user-selection [username]
  [:li [:a {:href (url "/Shibboleth.sso/Login" {:username username})} username]])

(defn- fake-login-screen [{session :session username :fake-username :as req}]
  (let [username (or username (-> req :params :username))]
    (if username
      (fake-login session username)
      (-> (html5 [:body [:ul (map user-selection ["developer" "alice" "bob"])]])
          (response)
          (content-type "text/html; charset=utf-8")))))

(defn- fake-logout [{session :session}]
  (-> (redirect "/")
      (assoc :session (dissoc session :identity))))

(defroutes fake-shibboleth-routes
  (GET "/Shibboleth.sso/Login" req (fake-login-screen req))
  (GET "/Shibboleth.sso/Logout" req (fake-logout req)))
