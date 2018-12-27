(ns rems.auth.util
  (:require [rems.auth.ForbiddenException]
            [rems.auth.ForbiddenException]))

(defn throw-unauthorized
  "Helper for throwing `NotAuthorizedException`."
  []
  (throw (rems.auth.NotAuthorizedException.)))

(defn throw-forbidden
  "Helper for throwing `ForbiddenException`."
  []
  (throw (rems.auth.ForbiddenException.)))
