(ns ^:figwheel-no-load rems.app
  (:require [rems.spa :as spa]))

(enable-console-print!)

(spa/init!)

(defn ^:export setUser [user]
  (spa/set-user! user))
