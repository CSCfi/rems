(ns rems.locales
  {:ns-tracker/resource-deps ["translations/en.edn" "translations/fi.edn"]}
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
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

(defn- recursive-keys [m]
  (mapcat (fn [[k v]]
            (if (map? v)
              (map (partial cons k) (recursive-keys v))
              [(list k)]))
          m))

(defn- unused-translation-keys [translations extras]
  (let [keys (set (recursive-keys translations))
        extra-keys (set (recursive-keys extras))]
    (seq (set/difference extra-keys keys))))

(defn- load-translation [language translations-directory theme-path]
  (let [filename (str (name language) ".edn")
        translations (translations-from-file filename translations-directory)
        extra-path (when theme-path (extra-translations-path theme-path))]
    (if (and extra-path (.exists (io/file extra-path)))
      (let [extra-translations (translations-from-file filename extra-path)]
        (when-let [unused (unused-translation-keys translations extra-translations)]
          (log/warn "Unused translation keys defined in" extra-path ":" unused))
        (deep-merge {language translations} {language extra-translations}))
      {language translations})))

(defn load-translations [{:keys [languages translations-directory theme-path]}]
  (if translations-directory
    (->> languages
         (mapv #(load-translation % translations-directory theme-path))
         (apply merge))
    (throw (RuntimeException. ":translations-directory was not set in config"))))

(defstate translations :start (load-translations env))

(defn tempura-config []
  (assert (map? translations) {:translations translations})
  {:dict translations})
