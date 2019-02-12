(ns rems.locales
  {:ns-tracker/resource-deps ["translations/en.edn" "translations/fi.edn"]}
  (:require [clojure.java.io :as io]
            [mount.core :refer [defstate]]
            [rems.config :refer [env]]
            [rems.util :refer [deep-merge]])
  (:import (java.io FileNotFoundException)))

(defn- load-translation [language translations-directory extra-translations-directory]
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
                                       (str "translations for " language " language could not be found in " resource-path " resource and " :translations-directory " was not set")))))
        result (deep-merge {language (read-string (slurp translation))}
                           {language {:t {:administration {:catalogue-items "Kettupossu items"}}}})] ;;TODO read from extra-translations-directory
    (clojure.pprint/pprint result)
    result))

(defn load-translations [{:keys [languages translations-directory extra-translations-directory]}]
  (->> languages
       (map #(load-translation % translations-directory extra-translations-directory))
       (apply merge)))

(defstate translations :start (load-translations env))

(defn tempura-config []
  {:dict translations})
