(ns ^:dev/once rems.app
  (:require [rems.identity :refer [set-identity!]]
            [rems.spa :as spa]))

(enable-console-print!)

(spa/init!)

(defn ^:export setIdentity [user-and-roles]
  (set-identity! user-and-roles))
