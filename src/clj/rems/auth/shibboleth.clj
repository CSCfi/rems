(ns rems.auth.shibboleth
  (:require [buddy.auth.middleware :refer [wrap-authentication
                                           wrap-authorization]]
            [haka-buddy.backend :refer [authz-backend shibbo-backend]]
            [hiccup.element :refer [image link-to]]
            [rems.context :as context]
            [rems.text :refer [text]]))

(defn login-component []
  [:div.m-auto.jumbotron
   [:h2 (text :t.login/title)]
   [:p (text :t.login/text)]
   (link-to (str context/*root-path* "/Shibboleth.sso/Login")
            (image {:class "login-btn"} "/img/haka-logo.jpg"))])

(defn wrap-auth [handler]
  (-> handler
      (wrap-authentication (shibbo-backend))
      (wrap-authorization (authz-backend))))
