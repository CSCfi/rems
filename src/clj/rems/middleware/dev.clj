(ns rems.middleware.dev
  (:require [ring.middleware.reload :refer [wrap-reload]]
            [prone.middleware :refer [wrap-exceptions]]))

(defn wrap-some-exceptions
  "Wrap some exceptions in the prone.middleware/wrap-exceptions,
  but let others pass (i.e. unauthorized)."
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (if (= :buddy.auth/unauthorized (:buddy.auth/type data))
            (throw e)
            ((wrap-exceptions (fn [& _] (throw e))) req))))
      (catch Throwable e
        ((wrap-exceptions (fn [& _] (throw e))) req)))))

(defn wrap-dev
  "Middleware for dev use. Autoreload, nicer errors."
  [handler]
  (-> handler
      wrap-reload
      wrap-some-exceptions))
