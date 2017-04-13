(ns rems.contents
  (:require [hiccup.element :refer [link-to image]]
            [rems.cart :as cart]
            [rems.context :as context]
            [rems.db.core :as db]
            [rems.form :as form]
            [rems.text :refer :all]))

(defn login [context]
  [:div.jumbotron
   [:h2 (text :t.login/title)]
   [:p (text :t.login/text)]
   (link-to (str context "/Shibboleth.sso/Login") (image {:class "login-btn"} "/img/haka-logo.jpg"))])

(defn about []
  [:p (text :t.about/text)])
