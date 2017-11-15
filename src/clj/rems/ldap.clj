(ns rems.ldap
  (:require [clj-ldap.client :as ldap]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET POST defroutes]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.config :refer [env]]
            [rems.guide :refer [example]]
            [rems.layout :as layout]
            [rems.text :refer [text]]
            [rems.util :refer [errorf getx getx-in]]
            [ring.util.response :refer [redirect]]))

;; Do these need to be configurable?
(def ^:private +ldap-search-attributes+ [:cn :displayName :company :mail])
(def ^:private +ldap-search-query+ "(cn=%s)")

(defn- get-ldap-user
  "Returns nil if login fails, map of properties if succeeds."
  [user password]
  (try
    (let [connection (assoc (getx-in env [:ldap :connection])
                            :bind-dn user :password password)
          search-root (getx-in env [:ldap :search-root])

          conn (ldap/connect connection)
          users (ldap/search conn search-root
                             {:filter (format +ldap-search-query+ user)
                              :attributes +ldap-search-attributes+})]
      (if (= 1 (count users))
        (first users)
        (do (log/errorf "Found %s hits for user %s" (count users) user)
            nil)))
    (catch com.unboundid.ldap.sdk.LDAPBindException e
      (log/errorf "Bind failed for user %s" user)
      nil)))

(defn- login-component []
  [:div.m-auto.jumbotron
   [:h2 (text :t.ldap/title)]
   [:form
    {:action "/ldaplogin" :method "post"}
    [:input.form-control {:type "text" :placeholder (text :t.ldap/username) :name "username" :required true}]
    [:input.form-control {:type "password" :placeholder (text :t.ldap/password) :name "password" :required true}]
    (anti-forgery-field)
    [:button.btn.btn-lg.btn-primary.btn-block {:type "submit"} (text :t.ldap/login)]]])

(defn- login-page []
  (layout/render
   "login"
   (login-component)))

(defn- login-failed []
  (assoc (redirect "/ldaplogin")
         :flash [{:status :failure :contents (text :t.ldap/failed)}]))

(defroutes ldap-routes
  (GET "/ldaplogin" [] (login-page))
  (POST "/ldaplogin" req
        (let [session (get req :session)
              username (getx-in req [:form-params "username"])
              password (getx-in req [:form-params "password"])
              user (get-ldap-user username password)]
          (if-not user
            (login-failed)
            (let [hack-user (assoc user
                                   "eppn" (getx user :cn)
                                   "commonName" (getx user :displayName))]
              (assoc (redirect "/landing_page")
                     :session (assoc session :identity hack-user)))))))

(defn guide []
  (list
   (example "login component"
            (login-component))))
