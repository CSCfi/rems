(ns rems.auth.shibboleth
  (:require [buddy.auth.middleware :refer [wrap-authentication
                                           wrap-authorization]]
            [haka-buddy.backend :refer [authz-backend shibbo-backend]]
            [hiccup.element :refer [image link-to]]
            [rems.context :as context]
            [rems.text :refer [text]]))

(defn wrap-auth [handler]
  (-> handler
      (wrap-authentication (shibbo-backend))
      (wrap-authorization (authz-backend))))

(defn login-url []
  "/Shibboleth.sso/Login")

(defn logout-url []
  "/Shibboleth.sso/Logout?return=%2F")
