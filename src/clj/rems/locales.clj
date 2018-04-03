(ns rems.locales
  (:require [taoensso.tempura :refer [load-resource-at-compile-time]]))

;; Note: the intermediate :t key in the dictionaries makes grepping
;; easier: all localizations are of the form :t/foo or :t.something/foo
(def tconfig
  {:dict
   {:en-GB {:__load-resource "translations/en-GB.edn"}
    :fi    {:__load-resource "translations/fi.edn"}
    :en :en-GB}})

(def translations
  {:en-GB (load-resource-at-compile-time "translations/en-GB.edn")
   :fi (load-resource-at-compile-time "translations/fi.edn")
   :en (load-resource-at-compile-time "translations/en-GB.edn")})
