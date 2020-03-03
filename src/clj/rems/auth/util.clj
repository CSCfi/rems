(ns rems.auth.util
  (:import [rems.auth UnauthorizedException ForbiddenException]))

(defn throw-unauthorized
  "Helper for throwing `UnauthorizedException`."
  []
  (throw (UnauthorizedException.)))

(defn throw-forbidden
  "Helper for throwing `ForbiddenException`."
  ([]
   (throw (ForbiddenException.)))
  ([msg]
   (throw (ForbiddenException. msg))))
