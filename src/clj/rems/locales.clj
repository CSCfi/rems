(ns rems.locales
  (:require [taoensso.tempura :refer [load-resource-at-compile-time]]))

;; Note: the intermediate :t key in the dictionaries makes grepping
;; easier: all localizations are of the form :t/foo or :t.something/foo
(def tconfig
  {:dict
   {:en {:__load-resource "translations/en.edn"}
    :fi {:__load-resource "translations/fi.edn"}}})

(def translations
  {:en (load-resource-at-compile-time "translations/en.edn")
   :fi (load-resource-at-compile-time "translations/fi.edn")})

