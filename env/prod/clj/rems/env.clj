(ns rems.env
  (:require [clojure.tools.logging :as log]))

(def +defaults+
  {:init
   (fn []
     (log/info "\n-=[rems started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[rems has shut down successfully]=-"))
   :middleware identity})

(def ^:dynamic *db* {:name "java:comp/env/jdbc/rems"})
