(ns rems.text
  (:require [rems.context :as context]))

(defn text
  "Return the tempura translation for a given key. Additional fallback
  keys can be given."
  [& keys]
  (context/*tempura* (vec keys)))
