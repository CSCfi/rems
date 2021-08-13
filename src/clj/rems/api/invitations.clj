(ns rems.api.invitations
  (:require [compojure.api.sweet :refer :all]
            [rems.api.services.invitation :as invitation]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.application.events :as events]
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]]
            [rems.schema-base :as schema-base]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(s/defschema CreateInvitationCommand
  {:email s/Str
   (s/optional-key :workflow-id) s/Int})

(s/defschema CreateInvitationResponse
  {:success s/Bool
   (s/optional-key :id) s/Int
   (s/optional-key :errors) [s/Any]})

(s/defschema InvitationResponse
  {(s/optional-key :invitation/id) s/Int
   :invitation/email s/Str
   :invitation/invited-by schema-base/UserWithAttributes
   (s/optional-key :invitation/invited-user) schema-base/UserWithAttributes
   :invitation/created DateTime
   (s/optional-key :invitation/sent) DateTime
   (s/optional-key :invitation/workflow) {:workflow/id s/Int}})

(def invitations-api
  (context "/invitations" []
    :tags ["invitations"]

    (GET "/" []
      :summary "Get invitations"
      :roles +admin-read-roles+
      :query-params [{disabled :- (describe s/Bool "whether to include disabled workflows") false}
                     {archived :- (describe s/Bool "whether to include archived workflows") false}]
      :return [InvitationResponse]
      (ok (invitation/get-invitations (merge (when-not disabled {:enabled true})
                                             (when-not archived {:archived false})))))

    (POST "/create" []
      :summary "Create an invitation. The invitation will be sent asynchronously to the recipient."
      :roles +admin-write-roles+
      :body [command CreateInvitationCommand]
      :return CreateInvitationResponse
      (ok (invitation/create-invitation! (assoc command :userid (getx-user-id)))))))
