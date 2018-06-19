(ns rems.auth.shibboleth
  (:require [rems.atoms :as atoms]
            [rems.navbar :as nav]
            [rems.text :refer [text]]))

(defn login-component []
  [:div.m-auto.jumbotron
   [:h2 (text :t.login/title)]
   [:p (text :t.login/text)]
   [:div.col-md6
    [atoms/link-to nil
                   (nav/url-dest "/Shibboleth.sso/Login")
                   [atoms/image {:class "login-btn"} "/img/haka-logo.jpg"]]]
   [atoms/link-to nil
                  (nav/url-dest "/Shibboleth.sso/eds")
                  (text :t.login/alternative)]])
