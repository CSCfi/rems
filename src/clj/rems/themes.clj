(ns rems.themes
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [cprop.source :as source]
            [mount.core :refer [defstate]]
            [rems.config :refer [env]]))

(defn load-default-theme []
  (source/from-resource "theme-defaults.edn"))

(defn load-theme [theme-path default-theme]
  (merge default-theme
         (when theme-path
           (try
             (assoc (source/from-file theme-path)
                    :theme-static-resources (.getPath (io/file (.getParentFile (io/file theme-path))
                                                               "public")))
             (catch java.util.MissingResourceException e
               (log/error (str "Could not locate the theme file: " theme-path))
               {})
             (catch java.lang.IllegalArgumentException e
               (log/error (str "Could not read the theme file: " theme-path
                               " Make sure that the file does not contain syntax errors."))
               {})))))

(defstate theme :start (load-theme (:theme-path env) (load-default-theme)))
