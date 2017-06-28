(ns rems.routes.services
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [rems.db.applications :refer [get-draft-id-for
                                          get-form-for]]))

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
           :application (s/maybe Long)
           :licenses [License]
           :title s/Str
           :items [Item]})

(defapi service-routes
  {:swagger {:ui "/swagger-ui"
             :spec "/swagger.json"
             :data {:info {:version "1.0.0"
                           :title "Sample API"
                           :description "Sample Services"}}}}

  (context "/api" []
           :tags ["form"]

           (GET "/form/:id" []
                :return       Form
                :path-params [id :- Long]
                :summary      "Form for a draft"
                (ok (let [app (get-draft-id-for id)]
                      (get-form-for id app))))

           (GET "/form/:id/:application" []
                :return       Form
                :path-params [id :- Long, application :- Long]
                :summary      "Form for an application"
                (ok (let [app (get-draft-id-for id)]
                      (get-form-for id app))))
           ))
