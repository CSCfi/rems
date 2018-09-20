(ns rems.locales
  (:require [mount.core :refer [defstate]]
            [rems.config :refer [env]]
            [clojure.java.io :as io])
  (:import (java.io FileNotFoundException)))

;; Note: the intermediate :t key in the dictionaries makes grepping
;; easier: all localizations are of the form :t/foo or :t.something/foo
; TODO: support external translations
(def tconfig
  {:dict
   {:en {:__load-resource "translations/en.edn"}
    :fi {:__load-resource "translations/fi.edn"}}})

(defn- load-translation [language translations-directory]
  (let [file (when translations-directory
               (io/file translations-directory (str (name language) ".edn")))
        resource-path (str "translations/" (name language) ".edn")
        resource (io/resource resource-path)
        translation (cond
                      (and file (.exists file)) file
                      resource resource
                      :else (throw (FileNotFoundException.
                                    (if file
                                      (str "translations for " language " language could not be found in " file " file or " resource-path " resource")
                                      (str "translations for " language " language could not be found in " resource-path " resource and " :translations-directory " was not set")))))]
    {language (read-string (slurp translation))}))

(defn load-translations [{:keys [languages translations-directory]}]
  (->> languages
       (map #(load-translation % translations-directory))
       (apply merge)))

(defstate translations :start (load-translations env))
