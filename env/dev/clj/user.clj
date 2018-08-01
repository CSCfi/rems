(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [eftest.runner :refer [find-tests run-tests]]))

(defn run-all-tests []
  (repl/refresh)
  (run-tests (find-tests "test") {:multithread? false}))
