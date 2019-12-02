(ns rems.auth.shibboleth
  (:require [buddy.auth.middleware :refer [wrap-authentication
                                           wrap-authorization]]
            [haka-buddy.backend :refer [shibbo-backend]]))

(defn backend []
  (shibbo-backend))

(defn login-url []
  "/Shibboleth.sso/Login")

(defn logout-url []
  "/Shibboleth.sso/Logout?return=%2F")
