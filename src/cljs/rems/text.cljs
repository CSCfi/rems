(ns rems.text
  (:require [re-frame.core :as rf]
            [taoensso.tempura :as tempura :refer [tr]]))

(defn text
  "Return the tempura translation for a given key. Additional fallback
  keys can be given."
  [& ks]
  (let [translations (rf/subscribe [:translations])
        language (rf/subscribe [:language])]
    (tr {:dict @translations}
        [@language]
        (conj (vec ks) :t/missing))))

(defn text-format
  "Return the tempura translation for a given key & format arguments"
  [k & args]
  (let [translations (rf/subscribe [:translations])
        language (rf/subscribe [:language])]
    (tr {:dict @translations}
        [@language]
        [k :t/missing]
        (vec args))))
