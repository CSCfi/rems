(ns rems.db.users
  (:require [rems.db.core :as db]
            [cheshire.core :refer [generate-string]]))

(defn add-user! [user userattrs]
  (assert (and userattrs user) "User or user attributes missing!")
  (db/add-user! {:user user :userattrs (generate-string userattrs)}))
