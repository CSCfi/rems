(ns rems.api.forms
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.api.util :refer [not-found-json-response extended-logging]]
            [rems.common.form :as common-form]
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]] ; required for route :roles
            [rems.config :refer [env]]
            [rems.schema-base :as schema-base]
            [rems.service.form]
            [ring.swagger.json-schema :as rjs]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defn- add-validation-errors [form]
  (assoc form :form/errors (common-form/validate-form-template form (:languages env))))

(defn- form-template-overview [form]
  (select-keys form [:form/errors
                     :form/external-title
                     :form/id
                     :form/internal-name
                     :form/title
                     :archived
                     :enabled
                     :organization]))

(defn- get-form-template [id]
  (when-let [form (rems.service.form/get-form-template id)]
    (-> form
        add-validation-errors)))

(defn- get-form-templates-overview [filters]
  (->> (rems.service.form/get-form-templates filters)
       (mapv add-validation-errors)
       (mapv form-template-overview)))

(s/defschema CreateFormCommand
  {:organization schema-base/OrganizationId
   (s/optional-key :form/title) (rjs/field (s/maybe s/Str)
                                           {:deprecate true
                                            :description "DEPRECATED, use internal name and external title instead"})
   (s/optional-key :form/internal-name) s/Str
   (s/optional-key :form/external-title) schema-base/LocalizedString
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
      (ok (get-form-templates-overview (merge (when-not disabled {:enabled true})
                                              (when-not archived {:archived false})))))

    (POST "/create" request
      :summary "Create form"
      :roles +admin-write-roles+
      :body [command CreateFormCommand]
      :return CreateFormResponse
      (extended-logging request)
      (ok (rems.service.form/create-form! command)))

    (GET "/:form-id" []
      :summary "Get form by id"
      :roles +admin-read-roles+
      :path-params [form-id :- (describe s/Int "form-id")]
      :return schema/FormTemplate
      (if-let [form (get-form-template form-id)]
        (ok form)
        (not-found-json-response)))

    (GET "/:form-id/editable" []
      :summary "Check if the form is editable"
      :roles +admin-write-roles+
      :path-params [form-id :- (describe s/Int "form-id")]
      :return schema/SuccessResponse
      (ok (rems.service.form/form-editable form-id)))

    (PUT "/edit" request
      :summary "Edit form"
      :roles +admin-write-roles+
      :body [command EditFormCommand]
      :return schema/SuccessResponse
      (extended-logging request)
      (ok (rems.service.form/edit-form! command)))

    (PUT "/archived" request
      :summary "Archive or unarchive form"
      :roles +admin-write-roles+
      :body [command schema/ArchivedCommand]
      :return schema/SuccessResponse
      (extended-logging request)
      (ok (rems.service.form/set-form-archived! command)))

    (PUT "/enabled" request
      :summary "Enable or disable form"
      :roles +admin-write-roles+
      :body [command schema/EnabledCommand]
      :return schema/SuccessResponse
      (extended-logging request)
      (ok (rems.service.form/set-form-enabled! command)))))
