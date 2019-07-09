(ns rems.auth.util
  (:import [rems.auth NotAuthorizedException ForbiddenException]))

(defn throw-unauthorized
  "Helper for throwing `NotAuthorizedException`."
  []
  (throw (NotAuthorizedException.)))

(defn throw-forbidden
  "Helper for throwing `ForbiddenException`."
  []
  (throw (ForbiddenException.)))
