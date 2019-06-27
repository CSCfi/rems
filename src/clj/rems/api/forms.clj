(ns rems.api.forms
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer [SuccessResponse UpdateStateCommand]]
            [rems.api.util]
            [rems.db.form :as form]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

(s/defschema Form
  {:id s/Num
   :organization s/Str
   :title s/Str
   :start DateTime
   :end (s/maybe DateTime)
   :expired s/Bool
   :enabled s/Bool
   :archived s/Bool})

(def not-neg? (partial <= 0))

(s/defschema FormField
  {:title {s/Keyword s/Str}
   :optional s/Bool
   :type (s/enum "attachment" "date" "description" "label" "multiselect" "option" "text" "texta")
   (s/optional-key :maxlength) (s/maybe (s/constrained s/Int not-neg?))
   (s/optional-key :options) [{:key s/Str
                               :label {s/Keyword s/Str}}]
   (s/optional-key :input-prompt) {s/Keyword s/Str}})

(s/defschema FormFieldWithId
  (merge FormField
         {:id s/Int}))

(s/defschema FullForm
  (merge Form
         {:fields [FormFieldWithId]}))

(s/defschema Forms
  [Form])

(defn- format-form
  [{:keys [id organization title start end expired enabled archived]}]
  {:id id
   :organization organization
   :title title
   :start start
   :end end
   :expired expired
   :enabled enabled
   :archived archived})

(defn- get-form-templates [filters]
  (doall
   (for [wf (form/get-form-templates filters)]
     (format-form wf))))

(s/defschema FormCommand
  {:organization s/Str
   :title s/Str
   :fields [FormField]})

(s/defschema FormResponse
  {:success s/Bool
   :id s/Num})

(def forms-api
  (context "/forms" []
    :tags ["forms"]

    (GET "/" []
      :summary "Get forms"
      :roles #{:owner}
      :query-params [{disabled :- (describe s/Bool "whether to include disabled forms") false}
                     {expired :- (describe s/Bool "whether to include expired forms") false}
                     {archived :- (describe s/Bool "whether to include archived forms") false}]
      :return Forms
      (ok (get-form-templates (merge (when-not expired {:expired false})
                                     (when-not disabled {:enabled true})
                                     (when-not archived {:archived false})))))

    (POST "/create" []
      :summary "Create form"
      :roles #{:owner}
      :body [command FormCommand]
      :return FormResponse
      (ok (form/create-form! (getx-user-id) command)))

    (GET "/:form-id" []
      :summary "Get form by id"
      :roles #{:owner}
      :path-params [form-id :- (describe s/Num "form-id")]
      :return FullForm
      (ok (form/get-form-template form-id)))

    (GET "/:form-id/editable" []
      :summary "Check if the form is editable"
      :roles #{:owner}
      :path-params [form-id :- (describe s/Num "form-id")]
      :return SuccessResponse
      (ok (form/form-editable form-id)))

    ;; TODO: PATCH would be more appropriate, but we use PUT elsewhere in the API
    (PUT "/:form-id/edit" []
      :summary "Edit form"
      :roles #{:owner}
      :path-params [form-id :- (describe s/Num "form-id")]
      :body [command FormCommand]
      :return SuccessResponse
      (ok (form/edit-form! (getx-user-id) form-id command)))

    ;; TODO: Change endpoint for updating form to be consistent with
    ;;   the endpoint for editing form (/:form-id/edit). Also change
    ;;   terminology to be less easily confused with form editing, e.g.,
    ;;   from /update to /:form-id/update-state.
    ;;
    ;;   For consistency, do similar change for catalogue items, licenses,
    ;;   and resources.
    (PUT "/update" []
      :summary "Update form"
      :roles #{:owner}
      :body [command UpdateStateCommand]
      :return SuccessResponse
      (ok (form/update-form! command)))))
