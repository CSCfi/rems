(ns rems.test.tempura
  (:require [rems.context :as context]))

(defn fake-tempura [[k]]
  (str k))

(defmacro with-fake-tempura [& body]
  `(binding [context/*tempura* fake-tempura]
     ~@body))

(defn fake-tempura-fixture [f]
  (with-fake-tempura
    (f)))
