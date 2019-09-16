(ns rems.auth.shibboleth
  (:require [rems.atoms :as atoms :refer [document-title]]
            [rems.flash-message :as flash-message]
            [rems.navbar :as nav]
            [rems.text :refer [text]]))

(defn login-component [alternative-endpoint]
  [:div
   [document-title (text :t.login/title)]
   [flash-message/component :top]
   [:p (text :t.login/text)]
   [:div
    [atoms/link nil
     (nav/url-dest "/Shibboleth.sso/Login")
     [atoms/image {:class "login-btn" :alt "Haka"} "/img/haka-logo.jpg"]]]
   (when alternative-endpoint
     [atoms/link nil
      (nav/url-dest alternative-endpoint)
      (text :t.login/alternative)])])
