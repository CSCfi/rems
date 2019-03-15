(ns rems.testing-tempura
  (:require [rems.context :as context]))

(defn fake-tempura [& args]
  (pr-str args))

(defmacro with-fake-tempura [& body]
  `(binding [context/*tempura* fake-tempura]
     ~@body))

(defn fake-tempura-fixture [f]
  (with-fake-tempura
    (f)))
