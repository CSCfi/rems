(ns ^:dev/once rems.app
  (:require [cognitect.transit]
            [re-frame.core :as rf]
            [rems.config]
            [rems.globals]
            [rems.spa]
            [rems.theme]
            [rems.user-settings]))

(enable-console-print!)

(defn- read-transit [value]
  (cognitect.transit/read (cognitect.transit/reader :json) value))

(defn ^:export setIdentity [user-and-roles]
  (let [parsed-values (read-transit user-and-roles)]
    (reset! rems.globals/user (:user parsed-values))
    (reset! rems.globals/roles (:roles parsed-values))
    (reset! rems.globals/language (rems.user-settings/get-language-cookie))))

(defn ^:export setConfig [config]
  (reset! rems.globals/config (read-transit config))
  (rems.user-settings/fetch-user-settings!))

(defn ^:export setTranslations [translations]
  (reset! rems.globals/translations (read-transit translations)))

(defn ^:export setTheme [theme]
  (reset! rems.globals/theme (read-transit theme)))

(defn ^:export setHandledOrganizations [organizations]
  (rf/dispatch-sync [:loaded-handled-organizations (read-transit organizations)]))
