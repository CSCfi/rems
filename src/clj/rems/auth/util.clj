(ns rems.auth.util)

(defn throw-unauthorized
  "Helper for throwing `NotAuthorizedException`."
  []
  (throw (rems.auth.NotAuthorizedException.)))

(defn throw-forbidden
  "Helper for throwing `ForbiddenException`."
  []
  (throw (rems.auth.ForbiddenException.)))
