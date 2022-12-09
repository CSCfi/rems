(ns rems.api.invitations
  (:require [compojure.api.sweet :refer :all]
            [rems.service.invitation :as invitation]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]]
            [rems.schema-base :as schema-base]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(s/defschema CreateInvitationCommand
  {:name s/Str
   :email s/Str
   (s/optional-key :workflow-id) s/Int})

(s/defschema CreateInvitationResponse
  {:success s/Bool
   (s/optional-key :invitation/id) s/Int
   (s/optional-key :errors) [s/Any]})

(s/defschema InvitationResponse
  {(s/optional-key :invitation/id) s/Int
   :invitation/name s/Str
   :invitation/email s/Str
   :invitation/invited-by schema-base/UserWithAttributes
   (s/optional-key :invitation/invited-user) schema-base/UserWithAttributes
   :invitation/created DateTime
   (s/optional-key :invitation/sent) DateTime
   (s/optional-key :invitation/accepted) DateTime
   (s/optional-key :invitation/workflow) {:workflow/id s/Int}})

(s/defschema AcceptInvitationResponse
  {:success s/Bool
   (s/optional-key :errors) [s/Any]
   (s/optional-key :invitation/workflow) {:workflow/id s/Int}})

(def invitations-api
  (context "/invitations" []
    :tags ["invitations"]

    (GET "/" []
      :summary "Get invitations"
      :roles +admin-read-roles+
      :query-params [{sent :- (describe s/Bool "whether to include sent invitations") nil}
                     {accepted :- (describe s/Bool "whether to include accepted invitations") nil}]
      :return [InvitationResponse]
      (ok (invitation/get-invitations (merge {:userid (getx-user-id)}
                                             (when (some? sent) {:sent sent})
                                             (when (some? accepted) {:accepted accepted})))))

    (POST "/create" []
      :summary "Create an invitation. The invitation will be sent asynchronously to the recipient."
      :roles +admin-write-roles+
      :body [command CreateInvitationCommand]
      :return CreateInvitationResponse
      (ok (invitation/create-invitation! (assoc command :userid (getx-user-id)))))

    (POST "/accept-invitation" []
      :summary "Accept an invitation. The invitation token will be spent."
      :roles #{:logged-in}
      :query-params [{token :- (describe s/Str "secret token of the invitation") false}]
      :return AcceptInvitationResponse
      (ok (invitation/accept-invitation! {:userid (getx-user-id) :token token})))))
