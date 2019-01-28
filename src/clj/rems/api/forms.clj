(ns rems.api.forms
  (:require [compojure.api.sweet :refer :all]
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
   :active s/Bool})

(s/defschema Forms
  [Form])

(defn- format-form
  [{:keys [id organization title start endt active?]}]
  {:id id
   :organization organization
   :title title
   :start start
   :end endt
   :active active?})

(defn- get-forms [filters]
  (doall
   (for [wf (form/get-forms filters)]
     (format-form wf))))

(s/defschema CreateFormCommand
  {:organization s/Str
   :title s/Str
   :items [{:title {s/Keyword s/Str}
            :optional s/Bool
            :type (s/enum "attachment" "date" "description" "label" "multiselect" "option" "text" "texta")
            (s/optional-key :maxlength) (s/maybe (s/constrained s/Int (comp not neg?)))
            (s/optional-key :options) [{:key s/Str
                                        :label {s/Keyword s/Str}}]
            (s/optional-key :input-prompt) {s/Keyword s/Str}}]})

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
      :return Form
      (ok (first (get-forms {:id form-id}))))

    (POST "/create" []
      :summary "Create form"
      :roles #{:owner}
      :body [command CreateFormCommand]
      :return CreateFormResponse
      (ok (form/create-form! (getx-user-id) command)))))
