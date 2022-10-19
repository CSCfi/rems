(ns rems.auth.fake
  (:require [rems.atoms :as atoms]
            [rems.navbar :as nav]
            [rems.text :refer [text]]))

(defn login-component []
  [:div
   (text :t.login/fake-title)
   (text :t.login/fake-text)
   [:div.text-center
    [atoms/link {:class "btn btn-primary btn-lg login-btn"}
     "/fake-login"
     (text :t.login/login)]]])
