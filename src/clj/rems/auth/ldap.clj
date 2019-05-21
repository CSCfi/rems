(ns rems.auth.ldap
  (:require [clj-ldap.client :as ldap]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET POST defroutes]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.config :refer [env]]
            [rems.layout :as layout]
            [rems.text :refer [text]]
            [rems.util :refer [getx getx-in]]
            [ring.util.response :refer [redirect]]))

;; Do these need to be configurable?
(def ^:private +ldap-search-attributes+ [:userPrincipalName :displayName :company :mail])
(def ^:private +ldap-search-query+ "(userPrincipalName=%s)")

(defn- get-ldap-user
  "Returns nil if login fails, map of properties if succeeds."
  [user password]
  (try
    (let [connection (assoc (getx-in env [:ldap :connection])
                            :bind-dn user
                            :password password)
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

;; TODO: should stop using "eppn" and instead convert both shibboleth
;; and ldap users to a common format.
(defn- convert-ldap-user
  "Converts user fetched from LDAP to a Shibboleth-like format."
  [user]
  {:eppn (getx user :userPrincipalName)
   :commonName (getx user :displayName)
   :mail (getx user :mail)
   :dn (getx user :dn)})

(defn login-component []
  [:div.jumbotron
   [:h1 (text :t.ldap/title)]
   [:form
    {:action "/ldap/login" :method "post"}
    [:input.form-control {:type "text" :placeholder (text :t.ldap/username) :name "username" :required true}]
    [:input.form-control {:type "password" :placeholder (text :t.ldap/password) :name "password" :required true}]
    (anti-forgery-field)
    [:button.btn.btn-lg.btn-primary.btn-block {:type :submit} (text :t.ldap/login)]]])

(defn login-url []
  "/ldap/login")

(defn logout-url []
  "/ldap/logout")

(defn- login-page []
  (layout/render
   (login-component)))

(defn- login-failed []
  (assoc (redirect "/ldap/login")
         :flash [{:status :failure :contents (text :t.ldap/failed)}]))

(defroutes routes
  (GET "/ldap/logout" req
    (let [session (get req :session)]
      (assoc (redirect "/#/redirect") :session (dissoc session :identity))))
  (GET "/ldap/login" [] (login-page))
  (POST "/ldap/login" req
    (let [session (get req :session)
          username (getx-in req [:form-params "username"])
          password (getx-in req [:form-params "password"])
          user (get-ldap-user username password)]
      (if user
        (assoc (redirect "/")
               :session (assoc session :identity (convert-ldap-user user)))
        (login-failed)))))
