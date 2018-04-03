(ns ^:figwheel-no-load rems.app
  (:require [rems.spa :as spa]))

(enable-console-print!)

(spa/init!)

(defn ^:export setIdentity [user-and-roles]
  (spa/set-identity! user-and-roles))
