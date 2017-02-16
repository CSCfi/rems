(ns rems.env
  (:require [clojure.tools.logging :as log]
            [rems.middleware.dev :refer [wrap-dev]]))

(def +defaults+
  {:init
   (fn []
     (log/info "\n-=[rems started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[rems has shut down successfully]=-"))
   :middleware wrap-dev
   :fake-shibboleth true})
