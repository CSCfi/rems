(ns rems.read-gitlog
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defmacro read-current-version []
  (try
    (-> "git-describe.txt"
        io/resource
        slurp
        str/trim)
    (catch java.io.IOException _ [])))