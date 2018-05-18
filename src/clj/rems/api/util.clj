(ns rems.api.util
  (:require [rems.auth.util :refer [throw-unauthorized]]
            [rems.roles :refer [has-roles?]]
            [rems.util :refer [get-user-id]]))

(defn check-user []
  (let [user-id (get-user-id)]
    (when-not user-id (throw-unauthorized))))

(defn check-roles [& roles]
  (when-not (apply has-roles? roles)
    (throw-unauthorized)))
