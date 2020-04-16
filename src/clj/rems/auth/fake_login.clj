(ns rems.auth.fake-login
  (:require [clojure.string :as str]
            [compojure.core :refer [GET defroutes]]
            [hiccup.page :refer [html5]]
            [hiccup.util :refer [url]]
            [rems.db.core :as db]
            [rems.db.users :as users]
            [ring.util.response :refer [content-type redirect response]]))

(def ^{:private true
       :doc "Inlined CSS declaration for fake login."}
  fake-login-styles "
html { height: 100%; color: #fff;}
body {
  min-height: 100%;
  font-size: 1em;
  display: flex;
  justify-content: center;
  align-items: center;
}
h1 { font-size: 3em; color: #333; text-align: center; font-variant: small-caps}
div.users {
  display: flex;
  justify-content: stretch;
  align-items: flex-start;
  flex-wrap: wrap;
  padding: 0;
  max-width: 800px;
}
div.user {
  flex-grow: 1;
  text-align: center;
  background-color: #777;
  margin: 0.25em;
  padding: 0.5em;
  border-radius: 0.2em;
}
div.user:hover {
  background-color: #333;
  cursor: pointer;
}
a { text-decoration: none; color: #fff; }
a:visited { color: #fff; }
")

(defn login-url []
  "/fake-login")

(defn logout-url []
  "/fake-logout")

(defn- fake-login [session username]
  (assoc (redirect "/redirect")
         :session (assoc session :identity (users/get-raw-user-attributes username))))

(defn- user-selection [username]
  (let [url (url (login-url) {:username username})]
    [:div.user {:onclick (str "window.location.href='" url "';")}
     [:a {:href url} username]]))

(defn- fake-login-screen [{session :session :as req}]
  (if-let [username (-> req :params :username)]
    (fake-login session username)
    (-> (html5 [:head
                [:title "Development Login"]
                [:style fake-login-styles]]
               [:body
                [:div.login
                 [:h1 "Development Login"]
                 [:div.users (->> (map :userid (db/get-users))
                                  (remove #(str/starts-with? % "perftester"))
                                  (sort)
                                  (distinct)
                                  (map user-selection))]]])
        (response)
        (content-type "text/html; charset=utf-8"))))

(defn- fake-logout [{session :session}]
  (assoc (redirect "/") :session (dissoc session :identity)))

(defroutes routes
  (GET (login-url) req (fake-login-screen req))
  (GET (logout-url) req (fake-logout req)))
