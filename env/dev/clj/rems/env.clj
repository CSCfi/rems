(ns rems.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [rems.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[rems started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[rems has shut down successfully]=-"))
   :middleware wrap-dev})
