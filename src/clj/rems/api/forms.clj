(ns rems.api.forms
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer [SuccessResponse UpdateStateCommand FormTemplateOverview NewFieldTemplate FormTemplate]]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.db.form :as form]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defn- get-form-templates [filters]
  (doall
   (for [form (form/get-form-templates filters)]
     (select-keys form [:form/id :form/organization :form/title :start :end :expired :enabled :archived]))))

(s/defschema CreateFormCommand
  {:form/organization s/Str
   :form/title s/Str
   :form/fields [NewFieldTemplate]})

(s/defschema EditFormCommand
  (assoc CreateFormCommand :form/id s/Int))

(s/defschema CreateFormResponse
  {:success s/Bool
   :id s/Int})

(def forms-api
  (context "/forms" []
    :tags ["forms"]

    (GET "/" []
      :summary "Get forms"
      :roles #{:owner}
      :query-params [{disabled :- (describe s/Bool "whether to include disabled forms") false}
                     {expired :- (describe s/Bool "whether to include expired forms") false}
                     {archived :- (describe s/Bool "whether to include archived forms") false}]
      :return [FormTemplateOverview]
      (ok (get-form-templates (merge (when-not expired {:expired false})
                                     (when-not disabled {:enabled true})
                                     (when-not archived {:archived false})))))

    (POST "/create" []
      :summary "Create form"
      :roles #{:owner}
      :body [command CreateFormCommand]
      :return CreateFormResponse
      (ok (form/create-form! (getx-user-id) command)))

    (GET "/:form-id" []
      :summary "Get form by id"
      :roles #{:owner}
      :path-params [form-id :- (describe s/Int "form-id")]
      :return FormTemplate
      (let [form (form/get-form-template form-id)]
        (if form
          (ok form)
          (not-found-json-response))))

    (GET "/:form-id/editable" []
      :summary "Check if the form is editable"
      :roles #{:owner}
      :path-params [form-id :- (describe s/Int "form-id")]
      :return SuccessResponse
      (ok (form/form-editable form-id)))

    ;; TODO: PATCH would be more appropriate, but we use PUT elsewhere in the API
    (PUT "/edit" []
      :summary "Edit form"
      :roles #{:owner}
      :body [command EditFormCommand]
      :return SuccessResponse
      (ok (form/edit-form! (getx-user-id) command)))

    (PUT "/update" []
      :summary "Update form"
      :roles #{:owner}
      :body [command UpdateStateCommand]
      :return SuccessResponse
      (ok (form/update-form! command)))))
