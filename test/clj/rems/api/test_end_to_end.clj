(ns ^:integration rems.api.test-end-to-end
  "Go from zero to an approved application. Check that all side-effects happen."
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]))

(use-fixtures :once api-fixture)

(defn extract-id [resp]
  (assert-success resp)
  (let [id (:id resp)]
    (assert (number? id) (pr-str resp))
    id))

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
            (:application-id
             (assert-success
              (api-call :post "/api/applications/create" {:catalogue-item-ids [catalogue-item-id]}
                        api-key applicant-id))))]


      (testing "fetch application as applicant"
        (assert (number? application-id))
        (let [application (api-call :get (str "/api/applications/" application-id) nil
                                    api-key applicant-id)]
          (is (= applicant-id (get-in application [:application/applicant :userid])))
          (is (= [resource-ext-id] (mapv :resource/ext-id (:application/resources application))))))

      (testing "check that application is visible"
        (let [applications (api-call :get "/api/my-applications" nil
                                     api-key applicant-id)]
          (is (= [application-id] (mapv :application/id applications)))))

      (testing "fill in application"
        (assert-success
         (api-call :post "/api/applications/save-draft" {:application-id application-id
                                                         :field-values [{:field 1
                                                                         :value "e2e test contents"}]}
                   api-key applicant-id)))

      (testing "accept terms of use"
        (assert-success
         (api-call :post "/api/applications/accept-licenses" {:application-id application-id
                                                              :accepted-licenses [license-id]}
                   api-key applicant-id)))

      (testing "submit application"
        (assert-success
         (api-call :post "/api/applications/submit" {:application-id application-id}
                   api-key applicant-id)))

      ;; TODO: check email to handler
      ;; TODO: check lack of entitlement

      (testing "fetch application as handler"
        (let [applications (api-call :get "/api/applications/todo" nil
                                     api-key handler-id)
              todos (set (map (juxt :application/id :application/todo) applications))]
          (is (contains? todos [application-id "new-application"])))
        (let [application (api-call :get (str "/api/applications/" application-id) nil
                                    api-key handler-id)]
          (is (= "e2e test contents" (get-in application [:application/form :form/fields 0 :field/value])))
          (is (= [license-id] (get-in application [:application/accepted-licenses (keyword applicant-id)]))
              application)))

      (testing "approve application"
        (assert-success
         (api-call :post "/api/applications/approve" {:application-id application-id
                                                      :comment "e2e approved"}
                   api-key handler-id)))

      ;; TODO: check email to applicant
      ;; TODO: check entitlement

      (testing "close application"
        (assert-success
         (api-call :post "/api/applications/close" {:application-id application-id
                                                    :comment "e2e closed"}
                   api-key handler-id)))

      ;; TODO: check entitlement ended

      (testing "fetch application as applicant"
        (let [application (api-call :get (str "/api/applications/" application-id) nil
                                    api-key applicant-id)]
          (is (= "application.state/closed" (:application/state application)))))
      )))
