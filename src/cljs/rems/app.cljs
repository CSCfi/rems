(ns ^:dev/once rems.app
  (:require [cognitect.transit]
            [re-frame.core :as rf]
            [rems.config]
            [rems.identity]
            [rems.spa]))

(enable-console-print!)

(defn- read-transit [value]
  (cognitect.transit/read (cognitect.transit/reader :json) value))

(defn ^:export setIdentity [user-and-roles]
  (let [{:keys [roles] :as user-and-roles} (read-transit user-and-roles)]
    (rf/dispatch-sync [:set-identity user-and-roles])
    (rf/dispatch-sync [:set-roles roles])))

(defn ^:export setConfig [config]
  (rf/dispatch-sync [:rems.config/loaded-config (read-transit config)]))

(defn ^:export setTranslations [translations]
  (rf/dispatch-sync [:loaded-translations (read-transit translations)]))

(defn ^:export setTheme [theme]
  (rf/dispatch-sync [:loaded-theme (read-transit theme)]))

