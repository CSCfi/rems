(ns rems.auth.shibboleth
  (:require [rems.atoms :as atoms]
            [rems.navbar :as nav]
            [rems.text :refer [text]]))

(defn login-component []
  [:div
   (text :t.login/shibboleth-title)
   (text :t.login/shibboleth-text)
   [:div.text-center
    [atoms/link nil
     (nav/url-dest "/Shibboleth.sso/Login")
     [atoms/image {:class "login-btn" :alt "Haka"} "/img/haka-logo.jpg"]]]])
