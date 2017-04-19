(ns rems.contents
  (:require [hiccup.element :refer [image link-to]]
            [rems.text :refer :all]))

(defn login [context]
  [:div.m-auto.jumbotron
   [:h2 (text :t.login/title)]
   [:p (text :t.login/text)]
   (link-to (str context "/Shibboleth.sso/Login") (image {:class "login-btn"} "/img/haka-logo.jpg"))])

(defn about []
  [:p (text :t.about/text)])
