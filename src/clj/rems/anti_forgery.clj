(ns rems.anti-forgery
  (:require [hiccup.form :refer [hidden-field]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

(defn anti-forgery-field
  "Create a hidden field with the session anti-forgery token as its value.
  This ensures that the form it's inside won't be stopped by the anti-forgery
  middleware.

  This is a replacement for the one in ring which renders the hiccup field always into text."
  []
  (hidden-field "__anti-forgery-token" *anti-forgery-token*))
