(ns rems.api.blacklist
  (:require [clojure.tools.logging :as log]
            [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.service.command :as command]
            [rems.service.blacklist :as blacklist]
            [rems.api.util :refer [unprocessable-entity-json-response]] ; required for route :roles
            [rems.application.rejecter-bot :as rejecter-bot]
            [rems.common.roles :refer [+admin-read-roles+]]
            [rems.db.resource :as resource]
            [rems.db.users :as users]
            [rems.db.user-mappings :as user-mappings]
            [rems.schema-base :as schema-base]
            [rems.util :refer [getx-in getx-user-id]]
            [ring.util.http-response :refer [ok]]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(s/defschema BlacklistCommand
  {:blacklist/resource {:resource/ext-id s/Str}
   :blacklist/user schema-base/User
   :comment s/Str})

(s/defschema BlacklistEntryWithDetails
  (assoc schema/BlacklistEntry
         :blacklist/comment s/Str
         :blacklist/added-by schema-base/UserWithAttributes
         :blacklist/added-at DateTime))

(defn- user-not-found-error [command]
  (when-not (users/user-exists? (get-in command [:blacklist/user :userid]))
    (unprocessable-entity-json-response "user not found")))

(defn- resource-not-found-error [command]
  (when-not (resource/ext-id-exists? (get-in command [:blacklist/resource :resource/ext-id]))
    (unprocessable-entity-json-response "resource not found")))

(def blacklist-api
  (context "/blacklist" []
    :tags ["blacklist"]

    (GET "/" []
      :summary "Get blacklist entries"
      :roles +admin-read-roles+
      :query-params [{user :- schema-base/UserId nil}
                     {resource :- s/Str nil}]
      :return [BlacklistEntryWithDetails]
      (ok (blacklist/get-blacklist {:userid (user-mappings/find-userid user)
                                    :resource/ext-id resource})))

    (GET "/users" []
      :summary "Existing REMS users available for adding to the blacklist"
      :roles  #{:owner :handler}
      :return [schema-base/UserWithAttributes]
      (ok (users/get-users)))

    ;; TODO write access to blacklist for organization-owner

    (POST "/add" []
      :summary "Add a blacklist entry"
      :roles #{:owner :handler}
      :body [command BlacklistCommand]
      :return schema/SuccessResponse
      (let [userid (user-mappings/find-userid (getx-in command [:blacklist/user :userid]))
            command (assoc-in command [:blacklist/user :userid] userid)]
        (or (user-not-found-error command)
            (resource-not-found-error command)
            (do (blacklist/add-user-to-blacklist! (getx-user-id) command)
                (doseq [cmd (rejecter-bot/reject-all-applications-by userid)]
                  (let [result (command/command! cmd)]
                    (when (:errors result)
                      (log/error "Failure when running rejecter bot commands:"
                                 {:cmd cmd :result result}))))
                (ok {:success true})))))

    (POST "/remove" []
      :summary "Remove a blacklist entry"
      :roles #{:owner :handler}
      :body [command BlacklistCommand]
      :return schema/SuccessResponse
      (let [userid (user-mappings/find-userid (getx-in command [:blacklist/user :userid]))
            command (assoc-in command [:blacklist/user :userid] userid)]
        (or (user-not-found-error command)
            (resource-not-found-error command)
            (do (blacklist/remove-user-from-blacklist! (getx-user-id) command)
                (ok {:success true})))))))
