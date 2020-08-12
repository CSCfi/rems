(ns rems.locales
  {:ns-tracker/resource-deps ["translations/en.edn" "translations/fi.edn" "translations/sv.edn"]}
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [medley.core :refer [deep-merge]]
            [mount.core :refer [defstate]]
            [rems.common.util :refer [recursive-keys]]
            [rems.config :refer [env]])
  (:import (java.io FileNotFoundException)))

(defn- file-or-resource
  "Given a path (a String or File), resolve it to either an existing file or existing resource.
  Returns a File or URL object, or nil if file nor resource can be found.
  Prefers files over resources."
  [path]
  (let [file (io/file path)
        resource (io/resource (str file))]
    (cond
      (and file (.exists file)) file
      resource resource
      :else nil)))

(defn- translations-from-file [file]
  (if-let [chosen (file-or-resource file)]
    (read-string (slurp chosen))
    (throw (FileNotFoundException.
            (str "translations could not be found in file or resource \"" file "\"")))))

(defn- extra-translations-path [theme-path]
  (let [theme-dir (.getParentFile (io/file theme-path))]
    (io/file theme-dir "extra-translations")))

(defn extract-format-parameters [string]
  (set
   (when (string? string)
     (re-seq #"%\d+" string))))

(defn- unused-translation-keys [translations extras]
  (let [keys (set (recursive-keys translations))
        extra-keys (set (recursive-keys extras))]
    (seq (set/difference extra-keys keys))))

(defn- nonmatching-format-parameters [translations extras]
  (seq
   (for [k (recursive-keys extras)
         :let [params (extract-format-parameters (get-in translations k))
               extra-params (extract-format-parameters (get-in extras k))]
         :when (not= params extra-params)]
     {:key k
      :translations params
      :extra-translations extra-params})))

(defn- load-translation [language translations-directory theme-path]
  (let [filename (str (name language) ".edn")
        translations (translations-from-file (io/file translations-directory filename))
        extra-path (when theme-path (extra-translations-path theme-path))]
    (if (and extra-path (.exists extra-path))
      (let [extra-file (io/file extra-path filename)
            extra-translations (translations-from-file extra-file)]
        (log/info "Loaded extra translations from" (str extra-file))
        (when-let [unused (unused-translation-keys translations extra-translations)]
          (log/warn "Unused translation keys defined in" (str extra-file) ":" unused))
        (when-let [errors (nonmatching-format-parameters translations extra-translations)]
          (log/warn "Nonmatching format parameters in" (str extra-file) ":" (pr-str errors)))
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
