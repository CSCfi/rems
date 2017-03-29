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
  (let [url (url "/Shibboleth.sso/Login" {:username username})]
    [:li {:onclick (str "window.location.href='" url "';")}
     [:a {:href url} username]]))

(defn- fake-login-screen [{session :session username :fake-username :as req}]
  (let [username (or username (-> req :params :username))]
    (if username
      (fake-login session username)
      (-> (html5 [:head [:style "
html { height: 100%; color: #fff;}
body {
  height: 100%;
  font-size: 3em;
  display: flex;
  justify-content: center;
  align-items: center;
}
h1 { color: #333; text-align: center; }
ul { padding: 0 }
li {
  list-style-type: none;
  text-align: center;
  background-color: #99135e;
  margin: 0.5em;
  padding: 0.2em;
  border-radius: 0.2em;
  text-transform: uppercase;
  cursor: pointer;
}
a { text-decoration: none; color: #fff; }
a:visited { color: #fff; }
"]]
                 [:body
                  [:div.login
                   [:h1 "Development Login"]
                   [:ul (map user-selection ["developer" "alice" "bob"])]]])
          (response)
          (content-type "text/html; charset=utf-8")))))

(defn- fake-logout [{session :session}]
  (-> (redirect "/")
      (assoc :session (dissoc session :identity))))

(defroutes fake-shibboleth-routes
  (GET "/Shibboleth.sso/Login" req (fake-login-screen req))
  (GET "/Shibboleth.sso/Logout" req (fake-logout req)))
