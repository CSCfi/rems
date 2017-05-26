(ns rems.locales)

;; Note: the intermediate :t key in the dictionaries makes grepping
;; easier: all localizations are of the form :t/foo or :t.something/foo
(def tconfig
  {:dict
   {:en-GB {:__load-resource "translations/en-GB.edn"}
    :fi    {:__load-resource "translations/fi.edn"}
    :en :en-GB}})
