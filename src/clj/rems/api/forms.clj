(ns rems.api.forms
  (:require [compojure.api.sweet :refer :all]
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
  [{:keys [id organization title start endt active? enabled archived]}]
  {:id id
   :organization organization
   :title title
   :start start
   :end endt
   :active active?
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
  {:id s/Num})

(def forms-api
  (context "/forms" []
    :tags ["forms"]

    (GET "/" []
      :summary "Get forms"
      :roles #{:owner}
      :query-params [{active :- (describe s/Bool "filter active or inactive forms") nil}]
      :return Forms
      (ok (get-forms (when-not (nil? active) {:active? active}))))

    (GET "/:form-id" []
      :summary "Get form by id"
      :roles #{:owner}
      :path-params [form-id :- (describe s/Num "form-id")]
      :return FullForm
      (ok (form/get-form form-id)))

    (POST "/create" []
      :summary "Create form"
      :roles #{:owner}
      :body [command CreateFormCommand]
      :return CreateFormResponse
      (ok (form/create-form! (getx-user-id) command)))))
