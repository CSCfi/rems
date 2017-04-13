(ns rems.db.users
  (:require [cheshire.core :refer [generate-string]]
            [rems.db.core :as db]))

(defn add-user! [user userattrs]
  (assert (and userattrs user) "User or user attributes missing!")
  (db/add-user! {:user user :userattrs (generate-string userattrs)}))
