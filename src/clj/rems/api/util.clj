(ns rems.api.util
  (:require [clojure.string :as str]
            [compojure.api.meta :refer [restructure-param]]
            [ring.util.http-response :as http-response]
            [rems.auth.util :refer [throw-unauthorized throw-forbidden]]
            [rems.common.roles :refer [has-roles?]]
            [rems.json :as json]
            [rems.util :refer [errorf get-user-id]]))

(defn check-user []
  (let [user-id (get-user-id)]
    (when-not user-id (throw-unauthorized))))

(defn check-roles [& roles]
  (when-not (apply has-roles? roles)
    (throw-forbidden)))

(defn add-roles-documentation [summary roles]
  (when (nil? summary)
    (throw (IllegalArgumentException. "Route must have a :summary when using :roles and it must be specified before :roles")))
  (let [role-str (->> roles
                      (map name)
                      (sort)
                      (str/join ", "))]
    `(str ~summary
          " (roles: "
          ~role-str
          ")")))

(defmethod restructure-param :roles [_ roles acc]
  (-> acc
      (update-in [:info :public :summary]
                 add-roles-documentation
                 (cond (coll? roles) roles
                       (symbol? roles) @(resolve roles)
                       :else (errorf "Don't know how to handle :roles %s" (pr-str roles))))
      (update-in [:lets] into ['_ `(do (check-user)
                                       (apply check-roles ~roles))])))

(defn not-found-json-response []
  (-> (http-response/not-found "{\"error\": \"not found\"}")
      (http-response/content-type "application/json")))

(defn not-found-text-response []
  (-> (http-response/not-found "not found")
      (http-response/content-type "text/plain")))

(defn unprocessable-entity-json-response [& [message]]
  (let [body (json/generate-string {:error (or message "unprocessable entity")})]
    (-> (http-response/unprocessable-entity body)
        (http-response/content-type "application/json"))))
