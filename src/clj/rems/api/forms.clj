(ns rems.api.forms
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [check-roles check-user]]
            [rems.db.form :as form]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

(def Form
  {:id s/Num
   :organization s/Str
   :title s/Str
   :start DateTime
   :end (s/maybe DateTime)
   :active s/Bool})

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

(def CreateFormCommand
  {:organization s/Str
   :title s/Str
   :items [{:title {s/Keyword s/Str}
            :optional s/Bool
            :type (s/enum "text" "texta" "date")
            :input-prompt (s/maybe {s/Keyword s/Str})}]})

(def forms-api
  (context "/forms" []
    :tags ["forms"]

    (GET "/" []
      :summary "Get forms"
      :query-params [{active :- (describe s/Bool "filter active or inactive forms") nil}]
      :return [Form]
      (check-user)
      (check-roles :owner)
      (ok (get-forms (when-not (nil? active) {:active? active}))))

    (POST "/create" []
      :summary "Create form"
      :body [command CreateFormCommand]
      (check-user)
      (check-roles :owner)
      (ok (form/create-form! command)))))
