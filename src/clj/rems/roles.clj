(ns rems.roles
  (:require [rems.context :as context]))

(defn has-roles?
  "Checks that the `context/*roles*` contains one of the given roles."
  [& roles]
  (assert (bound? #'context/*roles*) "context/*roles* is not defined")
  (boolean (some (set roles) context/*roles*)))
