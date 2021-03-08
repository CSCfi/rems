(ns rems.css.style-utils
  "The namespace contains the helpful style utils that are meant to be kept separate
   the main styles file to keep it cleaner."
  (:require [clojure.string :as str]
            [garden.stylesheet :as stylesheet]
            [rems.config :refer [env]]))


;; Customazable theme related functions

(defn get-theme-attribute
  "Fetch the attribute value from the current theme with fallbacks.

  Keywords denote attribute lookups while strings are interpreted as fallback constant value."
  [& attr-names]
  (when (seq attr-names)
    (let [attr-name (first attr-names)
          attr-value (if (keyword? attr-name)
                       (get (:theme env) attr-name)
                       attr-name)]
      (or attr-value (recur (rest attr-names))))))

(defn resolve-image [path]
  (when path
    (let [url (if (str/starts-with? path "http")
                path
                (str (get-theme-attribute :img-path "../../img/") path))]
      (str "url(\"" url "\")"))))

(defn get-logo-image [lang]
  (resolve-image (get-theme-attribute (keyword (str "logo-name-" (name lang))) :logo-name)))

(defn get-logo-name-sm [lang]
  (resolve-image (get-theme-attribute (keyword (str "logo-name-" (name lang) "-sm")) :logo-name-sm)))

(defn get-navbar-logo [lang]
  (resolve-image (get-theme-attribute (keyword (str "navbar-logo-name-" (name lang))) :navbar-logo-name)))

