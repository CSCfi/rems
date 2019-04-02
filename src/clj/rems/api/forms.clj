(ns rems.api.forms
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer [SuccessResponse UpdateStateCommand]]
            [rems.api.util]
            [rems.db.core :as db]
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
   :active s/Bool
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

(s/defschema FullForm
  (merge Form
         {:fields [s/Any]}))

(s/defschema Forms
  [Form])

(defn- format-form
  [{:keys [id organization title start endt active enabled archived]}]
  {:id id
   :organization organization
   :title title
   :start start
   :end endt
   :active active
   :enabled enabled
   :archived archived})

(defn- get-forms [filters]
  (doall
   (for [wf (form/get-forms filters)]
     (format-form wf))))

(s/defschema CreateFormCommand
  {:organization s/Str
   :title s/Str
   :items [FormField]})

(s/defschema CreateFormResponse
  {:success s/Bool
   :id s/Num})

;; TODO move to rems.db.form
(defn- update-form! [command]
  (let [catalogue-items (db/get-catalogue-items {:form (:id command)})]
    (if (seq catalogue-items)
      {:success false
       :errors [{:type :t.administration.errors/form-in-use :catalogue-items (mapv :id catalogue-items)}]}
      (do
        (db/set-form-state! command)
        (db/set-form-template-state! command)
        {:success true}))))

(def forms-api
  (context "/forms" []
    :tags ["forms"]

    (GET "/" []
      :summary "Get forms"
      :roles #{:owner}
      :query-params [{active :- (describe s/Bool "filter active or inactive forms") nil}]
      :return Forms
      (ok (get-forms (when active {:active active}))))

    (GET "/:form-id" []
      :summary "Get form by id"
      :roles #{:owner}
      :path-params [form-id :- (describe s/Num "form-id")]
      :return FullForm
      (ok (form/get-form form-id)))

    (GET "/v2/:form-id" []
      :summary "Get form by id"
      :roles #{:owner}
      :path-params [form-id :- (describe s/Num "form-id")]
      :return FullForm
      (ok (form/get-form-template form-id)))

    (POST "/create" []
      :summary "Create form"
      :roles #{:owner}
      :body [command CreateFormCommand]
      :return CreateFormResponse
      (ok (form/create-form! (getx-user-id) command)))

    (PUT "/update" []
      :summary "Update form"
      :roles #{:owner}
      :body [command UpdateStateCommand]
      :return SuccessResponse
      (ok (update-form! command)))))
