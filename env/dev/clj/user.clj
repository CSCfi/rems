(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [eftest.runner :as ef]))

(defn reload []
  (repl/refresh))

(defn run-tests [& namespaces]
  (reload)
  (ef/run-tests (ef/find-tests namespaces) {:multithread? false}))

(defn run-all-tests []
  (reload)
  (ef/run-tests (ef/find-tests "test/clj") {:multithread? false}))
