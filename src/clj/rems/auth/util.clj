(ns rems.auth.util)

(defn throw-unauthorized
  "Helper for throwing `NotAuthorizedException`."
  []
  (throw (rems.auth.NotAuthorizedException.)))
