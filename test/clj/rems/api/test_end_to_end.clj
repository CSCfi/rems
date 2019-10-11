(ns ^:integration rems.api.test-end-to-end
  "Go from zero to an approved application. Check that all side-effects happen."
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]))

(use-fixtures :once api-fixture)

(defn extract-id [{:keys [success id] :as resp}]
  (assert success (pr-str resp))
  (assert (number? id) (pr-str resp))
  id)

(deftest test-end-to-end
  (let [api-key "42"
        owner-id "owner"
        handler-id "e2e-handler"
        handler-attributes {:eppn handler-id
                            :commonName "E2E Handler"
                            :mail "handler@example.com"}
        applicant-id "e2e-applicant"
        applicant-attributes {:eppn applicant-id
                              :commonName "E2E Applicant"
                              :mail "applicant@example.com"}]
    (testing "create users"
      (api-call :post "/api/users/create" handler-attributes api-key owner-id)
      (api-call :post "/api/users/create" applicant-attributes api-key owner-id))

    (let [resource-ext-id "e2e-resource"

          resource-id
          (testing "create resource"
            (extract-id
             (api-call :post "/api/resources/create" {:resid resource-ext-id
                                                      :organization "e2e"
                                                      :licenses []}
                       api-key owner-id)))
          form-id
          (testing "create form"
            (extract-id
             (api-call :post "/api/forms/create" {:form/organization "e2e"
                                                  :form/title "e2e"
                                                  :form/fields [{:field/type :text
                                                                 :field/title {:en "text field"}
                                                                 :field/optional false}]}
                       api-key owner-id)))
          license-id
          (testing "create license"
            (extract-id
             (api-call :post "/api/licenses/create" {:licensetype "link"
                                                     :localizations {:en {:title "e2e license" :textcontent "http://example.com"}}}
                       api-key owner-id)))

          workflow-id
          (testing "create workflow"
            (extract-id
             (api-call :post "/api/workflows/create" {:organization "e2e"
                                                      :title "e2e workflow"
                                                      :type :dynamic
                                                      :handlers [handler-id]}
                       api-key owner-id)))

          catalogue-item-id
          (testing "create catalogue item"
            (extract-id
             (api-call :post "/api/catalogue-items/create" {:resid resource-id
                                                            :form form-id
                                                            :wfid workflow-id
                                                            :localizations {:en {:title "e2e catalogue item"}}}
                       api-key owner-id)))

          application-id
          (testing "create application"
            (:application-id (api-call :post "/api/applications/create" {:catalogue-item-ids [catalogue-item-id]}
                                       api-key applicant-id)))]


      (testing "fetch application"
        (assert (number? application-id))
        (let [application (api-call :get (str "/api/applications/" application-id) nil
                                    api-key applicant-id)]
          ;; just some basic sanity checks
          (is (= applicant-id (get-in application [:application/applicant :userid])))
          (is (= [resource-ext-id] (mapv :resource/ext-id (:application/resources application))))))

      ;; TODO: check email to handler
      ;; TODO: check lack of entitlement
      ;; TODO: submit
      ;; TODO: approve
      ;; TODO: check email to applicant
      ;; TODO: check entitlement

      )))
