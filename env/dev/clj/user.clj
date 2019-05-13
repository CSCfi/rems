(ns user
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :as repl]
            [kaocha.repl :as k]))

(defn reload []
  ((resolve 'rems.standalone/stop-app))
  (repl/refresh-all)
  ((resolve 'rems.standalone/start-app))
  ((resolve 'rems.validate/validate)))

(defn run-tests [& namespaces]
  (repl/refresh)
  (apply k/run namespaces))

(defn run-all-tests []
  (repl/refresh)
  (k/run))

(defn pptransit []
  ;; XXX: The user namespace is loaded before AOT happens, so our custom exceptions won't exist yet then,
  ;;      which causes the app to crash if we try to require an application namespace in the ns declaration.
  ;;      The workaround here is to defer requiring application namespaces.
  (require 'rems.repl-utils)
  ((resolve 'rems.repl-utils/pptransit)))
