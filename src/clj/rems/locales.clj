(ns rems.locales
  {:ns-tracker/resource-deps ["translations/en.edn" "translations/fi.edn"]}
  (:require [clojure.java.io :as io]
            [mount.core :refer [defstate]]
            [rems.config :refer [env]]
            [rems.util :refer [deep-merge]])
  (:import (java.io FileNotFoundException)))

(defn- translations-from-file [filename dir]
  (let [file (when dir
               (io/file dir filename))
        resource-path (str dir filename)
        resource (io/resource resource-path)
        file-contents (cond
                        (and file (.exists file)) file
                        resource resource
                        :else (throw (FileNotFoundException.
                                       (if file
                                         (str "translations could not be found in " file " file or " resource-path " resource")
                                         (str "translations could not be found in " resource-path " resource and " :translations-directory " was not set")))))]
    file-contents))

(defn- load-translation [language translations-directory extra-translations-directory]
  (let [filename (str (name language) ".edn")
        base-translations (translations-from-file filename translations-directory)]
    (if extra-translations-directory
      (deep-merge {language (read-string (slurp base-translations))}
                  {language (read-string (slurp (translations-from-file filename extra-translations-directory)))})
      {language (read-string (slurp base-translations))})))

(defn load-translations [{:keys [languages translations-directory]} & {:keys [extra-translations-directory]
                                                                       :or {extra-translations-directory nil}}]
  (if translations-directory
    (->> languages
         (map #(load-translation % translations-directory extra-translations-directory))
         (apply merge))
    (throw (RuntimeException. ":translations-directory was not set in config"))))

(defstate translations :start (load-translations env))

(defn tempura-config []
  {:dict translations})
