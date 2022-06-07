(ns ^:dev/once rems.app
  (:require [cognitect.transit]
            [rems.config]
            [rems.identity :refer [set-identity!]]
            [re-frame.core :as rf]
            [rems.spa]))

(enable-console-print!)

(defn ^:export setIdentity [user-and-roles]
  (set-identity! user-and-roles))

(defn- read-transit [value]
  (cognitect.transit/read (cognitect.transit/reader :json) value))

(defn ^:export setConfig [config]
  (rf/dispatch-sync [:rems.config/loaded-config (read-transit config)]))

(defn ^:export setTranslations [translations]
  (rf/dispatch-sync [:loaded-translations (read-transit translations)]))

(defn ^:export setTheme [theme]
  (rf/dispatch-sync [:loaded-theme (read-transit theme)]))

