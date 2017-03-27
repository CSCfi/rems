(ns rems.text
  (:require [rems.context :as context]))

(defn text
  "Return the tempura translation for a given key. Additional fallback
  keys can be given."
  [& keys]
  (context/*tempura* (conj (vec keys) :t/missing)))

(defn text-format
  "Return the tempura translation for a given key & format arguments"
  [key & args]
  (context/*tempura* [key :t/missing] (vec args)))
