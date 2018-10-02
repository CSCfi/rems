(ns user
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :as repl]
            [eftest.runner :as ef]))

(defn reload []
  ((resolve 'rems.standalone/stop-app))
  (repl/refresh-all)
  ((resolve 'rems.standalone/start-app)))

(defn run-tests [& namespaces]
  (repl/refresh)
  (ef/run-tests (ef/find-tests namespaces) {:multithread? false}))

(defn run-all-tests []
  (repl/refresh)
  (ef/run-tests (ef/find-tests "test/clj") {:multithread? false}))

(defn pptransit []
  ;; XXX: The user namespace is loaded before AOT happens, so our custom exceptions won't exist yet then,
  ;;      which causes the app to crash if we try to require an application namespace in the ns declaration.
  ;;      The workaround here is to defer requiring application namespaces.
  (require 'rems.repl-utils)
  ((resolve 'rems.repl-utils/pptransit)))
