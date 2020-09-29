(ns ^:integration rems.api.test-resources
  (:require [clojure.test :refer :all]
            [rems.db.core :as db]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :each
  api-fixture)

(defn- create-resource! [command api-key user-id]
  (-> (request :post "/api/resources/create")
      (authenticate api-key user-id)
      (json-body command)
      handler
      read-ok-body))

(defn- resource-archived! [command api-key user-id]
  (-> (request :put "/api/resources/archived")
      (authenticate api-key user-id)
      (json-body command)
      handler
      read-ok-body))

(defn- resource-enabled! [command api-key user-id]
  (-> (request :put "/api/resources/enabled")
      (authenticate api-key user-id)
      (json-body command)
      handler
      read-ok-body))

(deftest resources-api-create-test
  (let [api-key "42"
        owner "owner"
        org-owner "organization-owner1"
        licid-org1 (test-helpers/create-license! {:organization {:organization/id "organization1"}})
        licid-org2 (test-helpers/create-license! {:organization {:organization/id "organization2"}})
        resid "resource-api-test"]

    (doseq [user-id [owner org-owner]]
      (testing "create"
        (let [result (api-call :post "/api/resources/create"
                               {:resid resid :organization {:organization/id "organization1"} :licenses [licid-org1]}
                               api-key user-id)
              id (:id result)]
          (is (true? (:success result)))
          (is id)

          (testing "and fetch"
            (let [resource (api-call :get (str "/api/resources/" id) nil
                                     api-key user-id)]
              (is resource)
              (is (= [licid-org1] (map :id (:licenses resource))))))

          (testing "duplicate resource ID is allowed between organizations"
            ;; need to create as owner to have access to other org
            (let [result (api-call :post "/api/resources/create"
                                   {:resid resid :organization {:organization/id "test-organization2"} :licenses []}
                                   api-key owner)]
              (is (true? (:success result)))))

          (testing "duplicate resource ID is allowed within one organization"
            (let [result (api-call :post "/api/resources/create"
                                   {:resid resid :organization {:organization/id "organization1"} :licenses []}
                                   api-key user-id)]
              (is (true? (:success result))))))
        (testing "with mismatched organizations"
          (let [result (api-call :post "/api/resources/create"
                                 {:resid resid :organization {:organization/id "organization1"} :licenses [licid-org1 licid-org2]}
                                 api-key user-id)]
            (is (true? (:success result)))))))

    (testing "create as organization-owner with incorrect organization"
      (let [response (api-response :post "/api/resources/create"
                                   {:resid resid :organization {:organization/id "organization2"} :licenses [licid-org1 licid-org2]}
                                   api-key "organization-owner1")]
        (is (response-is-forbidden? response))
        (is (= "no access to organization \"organization2\"" (read-body response)))))))

(deftest resources-api-enable-archive-test
  (let [api-key "42"
        id (:id (create-resource! {:resid "enable-archive-test"
                                   :organization {:organization/id "organization1"}
                                   :licenses []}
                                  api-key "owner"))]
    (is (number? id))
    (doseq [user-id ["owner" "organization-owner1"]]
      (testing user-id
        (testing "disable"
          (is (:success (api-call :put "/api/resources/enabled"
                                  {:id id :enabled false}
                                  api-key user-id)))
          (testing "archive"
            (is (:success (api-call :put "/api/resources/archived"
                                    {:id id :archived true}
                                    api-key user-id))))
          (testing "fetch"
            (let [res (api-call :get (str "/api/resources/" id) {} api-key user-id)]
              (is (false? (:enabled res)))
              (is (true? (:archived res)))))
          (testing "unarchive"
            (is (:success (api-call :put "/api/resources/archived"
                                    {:id id :archived false}
                                    api-key user-id))))
          (testing "enable"
            (is (:success (api-call :put "/api/resources/enabled"
                                    {:id id :enabled true}
                                    api-key user-id))))
          (testing "fetch again"
            (let [res (api-call :get (str "/api/resources/" id) {} api-key user-id)]
              (is (true? (:enabled res)))
              (is (false? (:archived res))))))))
    (testing "as owner of different organization"
      (testing "disable"
        (is (response-is-forbidden? (api-response :put "/api/resources/enabled"
                                                  {:id id :enabled false}
                                                  api-key "organization-owner2"))))
      (testing "archive"
        (is (response-is-forbidden? (api-response :put "/api/resources/archived"
                                                  {:id id :archived true}
                                                  api-key "organization-owner2")))))))

(deftest resources-api-filtering-test
  (let [api-key "42"
        user-id "owner"
        enabled-id (:id (create-resource! {:resid "enabled"
                                           :organization {:organization/id "abc"}
                                           :licenses []}
                                          api-key user-id))
        _ (resource-enabled! {:id enabled-id :enabled true}
                             api-key user-id)
        _ (resource-archived! {:id enabled-id :archived false}
                              api-key user-id)
        disabled-id (:id (create-resource! {:resid "disabled"
                                            :organization {:organization/id "abc"}
                                            :licenses []}
                                           api-key user-id))
        _ (resource-enabled! {:id disabled-id :enabled false}
                             api-key user-id)
        _ (resource-archived! {:id disabled-id :archived false}
                              api-key user-id)
        archived-id (:id (create-resource! {:resid "archived"
                                            :organization {:organization/id "abc"}
                                            :licenses []}
                                           api-key user-id))
        _ (resource-enabled! {:id archived-id :enabled true}
                             api-key user-id)
        _ (resource-archived! {:id archived-id :archived true}
                              api-key user-id)]
    (testing "hides disabled and archived by default"
      (let [data (-> (request :get "/api/resources")
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)
            app-ids (set (map :id data))]
        (is (contains? app-ids enabled-id))
        (is (not (contains? app-ids disabled-id)))
        (is (not (contains? app-ids archived-id)))))
    (testing "includes disabled when requested"
      (let [data (-> (request :get "/api/resources?disabled=true")
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)
            app-ids (set (map :id data))]
        (is (contains? app-ids enabled-id))
        (is (contains? app-ids disabled-id))
        (is (not (contains? app-ids archived-id)))))
    (testing "includes archived when requested"
      (let [data (-> (request :get "/api/resources?archived=true")
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)
            app-ids (set (map :id data))]
        (is (contains? app-ids enabled-id))
        (is (not (contains? app-ids disabled-id)))
        (is (contains? app-ids archived-id))))))

(deftest resources-api-security-test
  (testing "without authentication"
    (testing "list"
      (let [response (-> (request :get "/api/resources")
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post "/api/resources/create")
                         (json-body {:resid "r"
                                     :organization {:organization/id "o"}
                                     :licenses []})
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "with wrong api key"
    (let [api-key "1"
          user-id "owner"]
      (testing "list"
        (let [response (-> (request :get "/api/resources")
                           (authenticate api-key user-id)
                           handler)]
          (is (response-is-unauthorized? response))
          (is (= "unauthorized" (read-body response)))))
      (testing "create"
        (let [response (-> (request :post "/api/resources/create")
                           (authenticate api-key user-id)
                           (json-body {:resid "r"
                                       :organization {:organization/id "o"}
                                       :licenses []})
                           handler)]
          (is (response-is-unauthorized? response))
          (is (= "Invalid anti-forgery token" (read-body response)))))))

  (testing "without owner role"
    (let [api-key "42"
          user-id "alice"]
      (testing "list"
        (let [response (-> (request :get "/api/resources")
                           (authenticate api-key user-id)
                           handler)]
          (is (response-is-forbidden? response))
          (is (= "forbidden" (read-body response)))))
      (testing "create"
        (let [response (-> (request :post "/api/resources/create")
                           (authenticate api-key user-id)
                           (json-body {:resid "r"
                                       :organization {:organization/id "o"}
                                       :licenses []})
                           handler)]
          (is (response-is-forbidden? response))
          (is (= "forbidden" (read-body response))))))))
