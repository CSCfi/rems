(ns user
  (:require [mount.core :as mount]
            rems.core))

(defn start []
  (mount/start-without #'rems.core/repl-server))

(defn stop []
  (mount/stop-except #'rems.core/repl-server))

(defn restart []
  (stop)
  (start))
