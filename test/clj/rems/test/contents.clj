(ns rems.test.contents
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [rems.context :as context]
            [rems.contents :refer :all]))

;; TODO: factor out if needed elsewhere
(use-fixtures
  :once
  (fn [f]
    (binding [context/*tempura* (fn [[k]] (str k))]
      (f))))
