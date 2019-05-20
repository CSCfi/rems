(ns rems.auth.shibboleth
  (:require [rems.atoms :as atoms]
            [rems.atoms :refer [document-title]]
            [rems.navbar :as nav]
            [rems.text :refer [text]]))

(defn login-component []
  [:div
   [document-title (text :t.login/title)]
   [:p (text :t.login/text)]
   [:div.col-md6
    [atoms/link-to nil
                   (nav/url-dest "/Shibboleth.sso/Login")
                   [atoms/image {:class "login-btn" :alt "Haka"} "/img/haka-logo.jpg"]]]])
