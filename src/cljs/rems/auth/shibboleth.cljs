(ns rems.auth.shibboleth
  (:require [rems.atoms :as atoms]
            [rems.navbar :as nav]
            [rems.text :refer [text]]))

(defn login-component [alternative-endpoint]
  [:div.jumbotron
   [:h2 (text :t.login/title)]
   [:p (text :t.login/text)]
   [:div.col-md6
    [atoms/link-to nil
                   (nav/url-dest "/Shibboleth.sso/Login")
                   [atoms/image {:class "login-btn" :alt "Haka"} "/img/haka-logo.jpg"]]]
   (when alternative-endpoint
    [atoms/link-to nil
                   (nav/url-dest alternative-endpoint)
                   (text :t.login/alternative)])])
