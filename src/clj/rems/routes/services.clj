(ns rems.routes.services
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [rems.db.applications :refer [get-draft-id-for
                                          get-form-for]]
            [rems.form :as form]
            [rems.context :as context]))

(def License
  {:id Long
   :type s/Str
   :licensetype s/Str
   :title s/Str
   :textcontent s/Str
   :approved s/Bool})

(def Item
  {:id Long
   :title s/Str
   :inputprompt (s/maybe s/Str)
   :optional s/Bool
   :type s/Str
   :value (s/maybe s/Str)})

(def Application
  {:id Long
   :catalogue-item Long
   :applicant-attributes {s/Str s/Str}
   :application s/Any
   :licenses [License]
   :title s/Str
   :items [Item]})

(def ValidationError s/Str)

(def SaveApplicationRequest
  {:operation s/Str
   (s/optional-key :application-id) Long
   :items {s/Keyword s/Str}  ;; NOTE: compojure-api only supports keywords here
   })

(def SaveApplicationResponse
  {:success s/Bool
   :valid s/Bool
   (s/optional-key :validation) [ValidationError]})

(defn fix-items-keys [application]
  (update-in application
             [:items]
             (fn [m]
               (into {} (for [[k v] m]
                          [(Long/parseLong (name k)) v])))))

(defapi service-routes
  {:swagger {:ui "/swagger-ui"
             :spec "/swagger.json"
             :data {:info {:version "1.0.0"
                           :title "Sample API"
                           :description "Sample Services"}}}}

  (context "/api" []
           :tags ["application"]

           (GET "/application/:resource-id" []
                :summary     "Get application by resource-id"
                :path-params [resource-id :- Long]
                :return      Application
                (let [app (get-draft-id-for resource-id)]
                  (ok (get-form-for resource-id app))))

           (GET "/application/:resource-id/:application-id" []
                :summary     "Get application by resource-id and application-id"
                :path-params [resource-id :- Long, application-id :- Long]
                :return      Application
                (ok (get-form-for resource-id application-id)))

           (PUT "/application/:resource-id" []
                :summary     "Put application by resource-id"
                :path-params [resource-id :- Long]
                :body        [request SaveApplicationRequest]
                :return      SaveApplicationResponse
                (ok (form/form-save resource-id (fix-items-keys request))))
           ))
