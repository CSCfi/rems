(ns rems.routes.services
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [rems.db.applications :refer [get-draft-id-for
                                          get-form-for]]
            [rems.form :as form]
            [rems.context :as context]))

(def License {:id Long
              :type s/Str
              :licensetype s/Str
              :title s/Str
              :textcontent s/Str
              :approved s/Bool})

(def Item {:id Long
           :title s/Str
           :inputprompt (s/maybe s/Str)
           :optional s/Bool
           :type s/Str
           :value (s/maybe s/Str)})

(def Form {:id Long
           :catalogue-item Long
           :applicant-attributes s/Any
           :application s/Any
           :licenses [License]
           :title s/Str
           :items [Item]})

(def Field {:name s/Str
            :value s/Str})

(def SaveFormRequest {:operation s/Str
                      (s/optional-key :application-id) Long
                      :fields [Field]})

(def SaveFormResponse {:success s/Bool})

(defapi service-routes
  {:swagger {:ui "/swagger-ui"
             :spec "/swagger.json"
             :data {:info {:version "1.0.0"
                           :title "Sample API"
                           :description "Sample Services"}}}}

  (context "/api" []
           :tags ["form"]

           (GET "/form/:resource-id" []
                :summary     "Form for a draft"
                :path-params [resource-id :- Long]
                :return      Form
                (let [app (get-draft-id-for resource-id)]
                  (ok (get-form-for resource-id app))))

           (GET "/form/:resource-id/:application-id" []
                :summary     "Form for an application"
                :path-params [resource-id :- Long, application-id :- Long]
                :return      Form
                (ok (get-form-for resource-id application-id)))

           (PUT "/form/:resource-id" []
                :summary     "Save a form"
                :path-params [resource-id :- Long]
                :body        [form SaveFormRequest]
                :return      SaveFormResponse
                (ok (form/form-save resource-id form)))
           ))
