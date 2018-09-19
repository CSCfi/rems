(ns rems.config
  (:require [clojure.java.io :as io]
            [cprop.core :refer [load-config]]
            [cprop.source :as source]
            [cprop.tools :refer [merge-maps]]
            [mount.core :refer [defstate]]))

(defn- file-exists? [path]
  (and path (.isFile (io/file path))))

(defn- file-sibling [file sibling-name]
  (.getPath (io/file (.getParentFile (io/file file))
                     sibling-name)))

(defn load-external-theme [config]
  (let [file (:theme-path config)]
    (if (file-exists? file)
      (assoc config
             :theme (merge-maps (:theme config) (source/from-file file))
             :theme-static-resources (file-sibling file "public"))
      config)))

(defstate env :start (-> (load-config :resource "config-defaults.edn")
                         (load-external-theme)))
