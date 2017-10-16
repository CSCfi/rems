(ns rems.routes.fake-shibboleth
  (:require [compojure.core :refer [GET defroutes]]
            [hiccup.page :refer [html5]]
            [hiccup.util :refer [url]]
            [ring.util.response :refer [content-type redirect
                                        response]]))

(def ^{:private true
       :doc "Inlined CSS declaration for fake login."}
  fake-login-styles "
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
")

(defn- fake-login [session username]
  (let [mail (get {"developer" "deve@lo.per" "alice" "a@li.ce" "bob" "b@o.b" "carl" "c@a.rl"} username)]
    (assoc (redirect "/landing_page")
      :session (assoc session :identity {"eppn" username "commonName" username "mail" mail}))))

(defn user-selection [username]
  (let [url (url "/Shibboleth.sso/Login" {:username username})]
    [:li {:onclick (str "window.location.href='" url "';")}
     [:a {:href url} username]]))

(defn- fake-login-screen [{session :session :as req}]
  (if-let [username (-> req :params :username)]
    (fake-login session username)
    (-> (html5 [:head [:style fake-login-styles]]
               [:body
                [:div.login
                 [:h1 "Development Login"]
                 [:ul (map user-selection ["developer" "alice" "bob" "carl"])]]])
        (response)
        (content-type "text/html; charset=utf-8"))))

(defn- fake-logout [{session :session}]
  (assoc (redirect "/") :session (dissoc session :identity)))

(defroutes fake-shibboleth-routes
  (GET "/Shibboleth.sso/Login" req (fake-login-screen req))
  (GET "/Shibboleth.sso/Logout" req (fake-logout req)))
