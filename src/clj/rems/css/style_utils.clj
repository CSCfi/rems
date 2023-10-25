(ns rems.css.style-utils
  "The namespace contains the helpful style utils that are meant to be kept separate
   the main styles file to keep it cleaner."
  (:require [clojure.string :as str]
            [rems.config :refer [env]]
            [rems.context :as context]))

(defn get-lang []
  (if (bound? #'context/*lang*)
    context/*lang*
    (env :default-language)))

(defn get-lang-name []
  (some-> (get-lang)
          name))

(defn css-var
  ([attr] (str "var(" (name attr) ")"))
  ([attr fallback] (str "var(" (name attr) ", " fallback ")")))

(defn css-url [uri]
  (str "url(\"" uri "\")"))

;; Customizable theme related functions

(def ^:private ignore-theme-var-error?
  ;; We don't want to include all these localized versions in
  ;; config-defaults.edn. Luckily there are only a few so we can just
  ;; have a whitelist.
  #{:logo-name-fi
    :logo-name-sm-fi
    :navbar-logo-name-fi
    :logo-name-sv
    :logo-name-sm-sv
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

(defn theme-get [& attrs]
  (when (seq attrs)
    (if-some [v (get-in env [:theme (first attrs)])]
      v
      (recur (rest attrs)))))

(defn- theme-logo [k]
  (case k
    :logo-name (keyword (str "logo-name-" (get-lang-name)))
    :logo-name-sm (keyword (str "logo-name-sm-" (get-lang-name)))
    :navbar-logo-name (keyword (str "navbar-logo-name-" (get-lang-name)))))

(defn theme-logo-get [k]
  (when-some [path (theme-get (theme-logo k) k)]
    (if (str/starts-with? path "http")
      path
      (str (theme-get :img-path) path))))

(defn theme-logo-getx [k]
  (let [path (theme-getx (theme-logo k) k)]
    (if (str/starts-with? path "http")
      path
      (str (theme-getx :img-path) path))))
