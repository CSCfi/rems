(ns rems.env
  (:require [clojure.tools.logging :as log]
            [conman.core :as conman]
            [mount.core :refer [defstate]]
            [rems.middleware.dev :refer [wrap-dev]]
            [rems.config :refer [env]]))

(def +defaults+
  {:init
   (fn []
     (log/info "\n-=[rems started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[rems has shut down successfully]=-"))
   :middleware wrap-dev
   :fake-shibboleth true
   :component-guide true})

(defstate ^:dynamic *db*
           :start (conman/connect! {:jdbc-url (env :database-url)})
           :stop (conman/disconnect! *db*))
