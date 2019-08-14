(ns rems.locales
  {:ns-tracker/resource-deps ["translations/en.edn" "translations/fi.edn"]}
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [mount.core :refer [defstate]]
            [rems.common-util :refer [deep-merge]]
            [rems.config :refer [env]])
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
    (read-string (slurp file-contents))))

(defn- extra-translations-path [theme-path]
  (let [path (str/join "/" (butlast (str/split theme-path #"/"))) ;;Theme-path is of form /foo/bar/theme.edn
        translations-path (str path "/extra-translations/")]
    translations-path))

(defn- load-translation [language translations-directory theme-path]
  (let [filename (str (name language) ".edn")]
    (if (and theme-path (.exists (io/file (extra-translations-path theme-path))))
      (deep-merge {language (translations-from-file filename translations-directory)}
                  {language (translations-from-file filename (extra-translations-path theme-path))})
      {language (translations-from-file filename translations-directory)})))

(defn load-translations [{:keys [languages translations-directory theme-path]}]
  (if translations-directory
    (->> languages
         (map #(load-translation % translations-directory theme-path))
         (apply merge))
    (throw (RuntimeException. ":translations-directory was not set in config"))))

(defstate translations :start (load-translations env))

(defn tempura-config []
  (assert (map? translations) {:translations translations})
  {:dict translations})
