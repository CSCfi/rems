(ns user
  (:require [mount.core :as mount]
            rems.standalone))

(defn start []
  (mount/start-without #'rems.standalone/repl-server))

(defn stop []
  (mount/stop-except #'rems.standalone/repl-server))

(defn restart []
  (stop)
  (start))
