(ns rems.text
  (:require [rems.context :as context]))

(defn text
  "Return the tempura translation for a given key. Additional fallback
  keys can be given."
  [& ks]
  (context/*tempura* (conj (vec ks) :t/missing)))

(defn text-format
  "Return the tempura translation for a given key & format arguments"
  [k & args]
  (context/*tempura* [k :t/missing] (vec args)))
