(ns rems.ldap
  (:require [clj-ldap.client :as ldap]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET POST defroutes]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.layout :as layout]
            [rems.text :refer [text]]
            [rems.util :refer [errorf getx getx-in]]
            [ring.util.response :refer [redirect]]))

(def ^:private +ldap-params+ {:host "localhost:2636" :ssl? true})

(def ^:private +ldap-search-root+ "dc=Suivohtor,dc=local")

(def ^:private +ldap-search-attributes+ [:cn :displayName :company :mail])

(defn- get-ldap-user
  "Returns nil if login fails, map of properties if succeeds."
  [user password]
  (try
    (let [conn (ldap/connect (assoc +ldap-params+ :bind-dn user :password password))
          users (ldap/search conn +ldap-search-root+
                             {:filter (format "(cn=%s)" user)
                              :attributes +ldap-search-attributes+})]
      (if (= 1 (count users))
        (first users)
        (do (log/errorf "Found %s hits for user %s" (count users) user)
            nil)))
    (catch com.unboundid.ldap.sdk.LDAPBindException e
      (log/errorf "Bind failed for user %s" user)
      nil)))

(defn- login-page []
  (layout/render
   "login"
   (list [:h1 (text :t.ldap/title)]
         [:form
          {:action "/ldaplogin" :method "post"}
          [:input {:type "text" :placeholder (text :t.ldap/username) :name "username" :required true}]
          [:input {:type "password" :placeholder (text :t.ldap/password) :name "password" :required true}]
          (anti-forgery-field)
          [:button {:type "submit"} (text :t.ldap/login)]])))

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
