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


;; Fonts of the app 

(defn generate-at-font-faces
  "The theme :font-family settings will override these fonts is set.
  
  Reason for this function to be included into our screen.css
   - because themes like Findata can use them since our theme.edn doesn't offer a possibility to add custom fonts
   - all the fonts need to be 'built in' to REMS, even if they're not active
   - bundling Robot Slab and Lato in REMS is a bit of a hack, made for Findata & THL to get the sort of styles they want"
  []
  (list
   (stylesheet/at-font-face {:font-family "'Lato'"
                             :src "url('/font/Lato-Light.eot')"}
                            {:src "url('/font/Lato-Light.eot') format('embedded-opentype'), url('/font/Lato-Light.woff2') format('woff2'), url('/font/Lato-Light.woff') format('woff'), url('/font/Lato-Light.ttf') format('truetype')"
                             :font-weight 300
                             :font-style "normal"})
   (stylesheet/at-font-face {:font-family "'Lato'"
                             :src "url('/font/Lato-Regular.eot')"}
                            {:src "url('/font/Lato-Regular.eot') format('embedded-opentype'), url('/font/Lato-Regular.woff2') format('woff2'), url('/font/Lato-Regular.woff') format('woff'), url('/font/Lato-Regular.ttf') format('truetype')"
                             :font-weight 400
                             :font-style "normal"})
   (stylesheet/at-font-face {:font-family "'Lato'"
                             :src "url('/font/Lato-Bold.eot')"}
                            {:src "url('/font/Lato-Bold.eot') format('embedded-opentype'), url('/font/Lato-Bold.woff2') format('woff2'), url('/font/Lato-Bold.woff') format('woff'), url('/font/Lato-Bold.ttf') format('truetype')"
                             :font-weight 700
                             :font-style "normal"})
   (stylesheet/at-font-face {:font-family "'Roboto Slab'"
                             :src "url('/font/Roboto-Slab.woff2') format('woff2')"
                             :font-weight 400
                             :font-style "normal"})))