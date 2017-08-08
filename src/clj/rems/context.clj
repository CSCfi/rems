(ns rems.context
  "Collection of the global variables for REMS.

   When referring, please make your use greppable with the prefix context,
   i.e. context/*root-path*."
  (:require [clojure.tools.logging :as log]
            [cprop.core :refer [load-config]]
            [cprop.source :refer [from-resource]]))

(defn load-default-theme []
  (from-resource "themes/default.edn"))

(defn load-theme
  "Tries to load the default theme and override default values by merging them with the values from the theme given as a parameter."
  ([theme]
   (merge (load-default-theme)
          (when theme
            (try (from-resource (str "themes/" theme ".edn"))
              (catch java.util.MissingResourceException e
                (log/error (str "Could not locate a theme file by the name: "
                                (str theme ".edn")))
                {})
              (catch java.lang.IllegalArgumentException e
                (log/error (str "Could not read the theme configuration for resource"
                                (str " \"" theme ".edn\". ")
                                "Make sure that the file does not contain syntax errors."))
                {})))))
  ([]
   (load-theme (:theme (load-config)))))

(def ^:dynamic ^{:doc "Application root path also known as context-path.

  If application does not live at '/',
  then this is the path before application relative paths."}
  *root-path*)

(def ^:dynamic ^{:doc "User data available from request."} *user*)

(def ^:dynamic ^{:doc "Active role for user or nil"} *active-role*)

(def ^:dynamic ^{:doc "Set of roles for user (or nil)"} *roles*)

(def ^:dynamic ^{:doc "Tempura object initialized with user's preferred language."}
  *tempura*)

(def ^:dynamic ^{:doc "User's preferred language."} *lang*)

(def ^:dynamic ^{:doc "Contents of the cart."} *cart*)

(def ^:dynamic ^{:doc "Flash session."} *flash*)

(def ^:dynamic ^{:doc "Theme related stylings for the site."} *theme* (load-theme))
