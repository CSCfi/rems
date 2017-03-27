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
      (catch Throwable t
        (if (instance? clojure.lang.ExceptionInfo t)
          (let [data (ex-data t)]
            (if (= :buddy.auth/unauthorized (:buddy.auth/type data))
              (throw t)
              ((wrap-exceptions (fn [& _] (throw t))) req)))
          (throw t))))))

(defn wrap-dev
  "Middleware for dev use. Autoreload, nicer errors."
  [handler]
  (-> handler
      wrap-reload
      wrap-some-exceptions))
