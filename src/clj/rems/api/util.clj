(ns rems.api.util
  (:require [clojure.string :as str]
            [compojure.api.meta :refer [restructure-param]]
            [rems.auth.util :refer [throw-unauthorized throw-forbidden]]
            [rems.roles :refer [has-roles?]]
            [rems.util :refer [get-user-id]]))

(defn check-user []
  (let [user-id (get-user-id)]
    (when-not user-id (throw-unauthorized))))

(defn check-roles [& roles]
  (when-not (apply has-roles? roles)
    (throw-forbidden)))

(defn longify-keys [m]
  (into {} (for [[k v] m]
             (if (keyword? k)
               [(Long/parseLong (name k)) v]
               [k v]))))

(defn add-roles-documentation [summary roles]
  (when (nil? summary)
    (throw (IllegalArgumentException. "Route must have a :summary when using :roles and it must be specified before :roles")))
  (str summary
       " (roles: "
       (->> roles
            (map name)
            (sort)
            (str/join ", "))
       ")"))

(defmethod restructure-param :roles [_ roles acc]
  (-> acc
      (update-in [:info :public :summary] add-roles-documentation roles)
      (update-in [:lets] into ['_ `(do (check-user)
                                       (check-roles ~@roles))])))
