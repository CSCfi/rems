(ns rems.auth.util
  (:require [rems.auth.NotAuthorizedException]))

(defn throw-unauthorized
  "Helper for throwing `NotAuthorizedException`."
  []
  (throw (rems.auth.NotAuthorizedException.)))
