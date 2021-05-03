(ns rems.css.style-utils
  "The namespace contains the helpful style utils that are meant to be kept separate
   the main styles file to keep it cleaner."
  (:require [clojure.string :as str]
            [rems.config :refer [env]]))


;; Customizable theme related functions

(def ^:private ignore-theme-var-error?
  ;; We don't want to include all these localized versions in
  ;; config-defaults.edn. Luckily there are only a few so we can just
  ;; have a whitelist.
  #{:logo-name-fi
    :logo-name-fi-sm
    :navbar-logo-name-fi
    :logo-name-sv
    :logo-name-sv-sm
    :navbar-logo-name-sv})

(defn- theme-getx-impl
  "Helper for making sure we document all our theme variables."
  [attr]
  (if-let [[_ v] (find (:theme env) attr)] ; find instead of get: nil values are ok, missing values are bad
    v
    (when (:dev env)
      (when-not (ignore-theme-var-error? attr)
        (assert false (str "Theme attribute " attr " used but not documented in config-defaults.edn!"))))))

(defn theme-getx
  "Fetch the attribute value from the current theme with fallbacks.

  Keywords denote attribute lookups while strings are interpreted as fallback constant value."
  [& attr-names]
  (when (seq attr-names)
    (let [attr-name (first attr-names)
          attr-value (if (keyword? attr-name)
                       (theme-getx-impl attr-name)
                       attr-name)]
      (or attr-value (recur (rest attr-names))))))

(defn resolve-image [path]
  (when path
    (let [url (if (str/starts-with? path "http")
                path
                (str (theme-getx :img-path) path))]
      (str "url(\"" url "\")"))))

(defn get-logo-image [lang]
  (resolve-image (theme-getx (keyword (str "logo-name-" (name lang))) :logo-name)))

(defn get-logo-name-sm [lang]
  (resolve-image (theme-getx (keyword (str "logo-name-" (name lang) "-sm")) :logo-name-sm)))

(defn get-navbar-logo [lang]
  (resolve-image (theme-getx (keyword (str "navbar-logo-name-" (name lang))) :navbar-logo-name)))
