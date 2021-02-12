(ns rems.api.forms
  (:require [compojure.api.sweet :refer :all]
            [rems.api.services.form :as form]
            [rems.api.schema :as schema]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defn- get-form-templates [filters]
  (doall
   (for [form (form/get-form-templates filters)]
     (select-keys form [:form/id :organization :form/title :form/errors :enabled :archived]))))

(s/defschema CreateFormCommand
  {:organization schema/OrganizationId
   :form/title s/Str
   :form/fields [schema/NewFieldTemplate]})

(s/defschema EditFormCommand
  (assoc CreateFormCommand :form/id s/Int))

(s/defschema CreateFormResponse
  {:success s/Bool
   (s/optional-key :id) s/Int
   (s/optional-key :errors) [s/Any]})

(def forms-api
  (context "/forms" []
    :tags ["forms"]

    (GET "/" []
      :summary "Get forms"
      :roles +admin-read-roles+
      :query-params [{disabled :- (describe s/Bool "whether to include disabled forms") false}
                     {archived :- (describe s/Bool "whether to include archived forms") false}]
      :return [schema/FormTemplateOverview]
      (ok (get-form-templates (merge (when-not disabled {:enabled true})
                                     (when-not archived {:archived false})))))

    (POST "/create" []
      :summary "Create form"
      :roles +admin-write-roles+
      :body [command CreateFormCommand]
      :return CreateFormResponse
      (ok (form/create-form! (getx-user-id) command)))

    (GET "/:form-id" []
      :summary "Get form by id"
      :roles +admin-read-roles+
      :path-params [form-id :- (describe s/Int "form-id")]
      :return schema/FormTemplate
      (let [form (form/get-form-template form-id)]
        (if form
          (ok form)
          (not-found-json-response))))

    (GET "/:form-id/editable" []
      :summary "Check if the form is editable"
      :roles +admin-write-roles+
      :path-params [form-id :- (describe s/Int "form-id")]
      :return schema/SuccessResponse
      (ok (form/form-editable form-id)))

    (PUT "/edit" []
      :summary "Edit form"
      :roles +admin-write-roles+
      :body [command EditFormCommand]
      :return schema/SuccessResponse
      (ok (form/edit-form! (getx-user-id) command)))

    (PUT "/archived" []
      :summary "Archive or unarchive form"
      :roles +admin-write-roles+
      :body [command schema/ArchivedCommand]
      :return schema/SuccessResponse
      (ok (form/set-form-archived! command)))

    (PUT "/enabled" []
      :summary "Enable or disable form"
      :roles +admin-write-roles+
      :body [command schema/EnabledCommand]
      :return schema/SuccessResponse
      (ok (form/set-form-enabled! command)))))
