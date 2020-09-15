(ns rems.api.forms
  (:require [compojure.api.sweet :refer :all]
            [rems.api.services.form :as form]
            [rems.api.schema :refer [ArchivedCommand EnabledCommand FormTemplate FormTemplateOverview NewFieldTemplate OrganizationId SuccessResponse]]
            [rems.api.util :refer [+admin-read-roles+ +admin-write-roles+ not-found-json-response]] ; required for route :roles
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defn- get-form-templates [filters]
  (doall
   (for [form (form/get-form-templates filters)]
     (select-keys form [:form/id :organization :form/title :form/errors :enabled :archived]))))

(s/defschema CreateFormCommand
  {:organization OrganizationId
   :form/title s/Str
   :form/fields [NewFieldTemplate]})

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
      :return [FormTemplateOverview]
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
      :return FormTemplate
      (let [form (form/get-form-template form-id)]
        (if form
          (ok form)
          (not-found-json-response))))

    (GET "/:form-id/editable" []
      :summary "Check if the form is editable"
      :roles +admin-write-roles+
      :path-params [form-id :- (describe s/Int "form-id")]
      :return SuccessResponse
      (ok (form/form-editable form-id)))

    (PUT "/edit" []
      :summary "Edit form"
      :roles +admin-write-roles+
      :body [command EditFormCommand]
      :return SuccessResponse
      (ok (form/edit-form! (getx-user-id) command)))

    (PUT "/archived" []
      :summary "Archive or unarchive form"
      :roles +admin-write-roles+
      :body [command ArchivedCommand]
      :return SuccessResponse
      (ok (form/set-form-archived! command)))

    (PUT "/enabled" []
      :summary "Enable or disable form"
      :roles +admin-write-roles+
      :body [command EnabledCommand]
      :return SuccessResponse
      (ok (form/set-form-enabled! command)))))
