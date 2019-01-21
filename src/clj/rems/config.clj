(ns rems.config
  (:require [clojure.java.io :as io]
            [cprop.core :refer [load-config]]
            [cprop.source :as source]
            [cprop.tools :refer [merge-maps]]
            [mount.core :refer [defstate]])
  (:import (java.io FileNotFoundException)))

(defn- file-sibling [file sibling-name]
  (.getPath (io/file (.getParentFile (io/file file))
                     sibling-name)))

(defn- get-file [config key]
  (if-let [file (get config key)]
    (if (.isFile (io/file file))
      file
      (throw (FileNotFoundException. (str "the file specified in " key " does not exist: " file))))))

(defn load-external-theme [config]
  (if-let [file (get-file config :theme-path)]
    (merge-maps config
                {:theme (source/from-file file)
                 :theme-static-resources (file-sibling file "public")})
    config))

(defstate env :start (-> (load-config :resource "config-defaults.edn"
                                      ;; Precedence:
                                      ;; 1. Use `rems.config` system property if defined
                                      ;; 2. Use `conf` system property if defined (hard-coded in cprop)
                                      ;; 3. Ignore `:file`
                                      :file (System/getProperty "rems.config"))
                         (load-external-theme)))
