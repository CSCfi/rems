(ns ^:figwheel-no-load rems.app
  (:require [rems.spa :as spa]
            [devtools.core :as devtools]))

(enable-console-print!)

(devtools/install!)

(spa/init!)

(defn ^:export setUser [user]
  (spa/set-user! user))
