(ns ^:integration rems.api.test-resources
  (:require [clojure.test :refer :all]
            [rems.db.core :as db]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [owners-fixture +test-api-key+]]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :each
  api-fixture
  owners-fixture)

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
  (let [owner "owner"
        org-owner "organization-owner1"
        licid-org1 (test-helpers/create-license! {:organization {:organization/id "organization1"}})
        licid-org2 (test-helpers/create-license! {:organization {:organization/id "organization2"}})
        resid "resource-api-test"]

    (testing "fetch nonexistent"
      (let [response (api-response :get "/api/resources/9999999" nil
                                   +test-api-key+ owner)]
        (is (response-is-not-found? response))
        (is (= {:error "not found"} (read-body response)))))

    (doseq [user-id [owner org-owner]]
      (testing "create"
        (let [result (api-call :post "/api/resources/create"
                               {:resid resid :organization {:organization/id "organization1"} :licenses [licid-org1]}
                               +test-api-key+ user-id)
              id (:id result)]
          (is (true? (:success result)))
          (is id)

          (testing "and fetch"
            (let [resource (api-call :get (str "/api/resources/" id) nil
                                     +test-api-key+ user-id)]
              (is resource)
              (is (= [licid-org1] (map :id (:licenses resource)))))))
        (testing "duplicate resource ID is allowed between organizations"
          ;; need to create as owner to have access to other org
          (let [result (api-call :post "/api/resources/create"
                                 {:resid resid :organization {:organization/id "test-organization2"} :licenses []}
                                 +test-api-key+ owner)]
            (is (true? (:success result)))))

        (testing "duplicate resource ID is allowed within one organization"
          (let [result (api-call :post "/api/resources/create"
                                 {:resid resid :organization {:organization/id "organization1"} :licenses []}
                                 +test-api-key+ user-id)]
            (is (true? (:success result))))))

      (testing "DUO codes"
        (testing "DUO not enabled"
          (with-redefs [rems.config/env (:enable-duo false)]
            (let [result (api-response :post "/api/resources/create"
                                       {:resid "duo-test-resource"
                                        :organization {:organization/id "organization1"}
                                        :licenses [licid-org1]
                                        :resource/duo {:duo/codes [{:id "DUO:0000007" :restrictions [{:type :mondo :values [{:id "MONDO:0000004"}]}]}
                                                                   {:id "DUO:0000021"}
                                                                   {:id "DUO:0000027" :restrictions [{:type :project :values [{:value "CSC/REMS"}]}]}]}}
                                       +test-api-key+ user-id)]
              (is (response-is-bad-request? result)))))

        (testing "DUO enabled"
          (with-redefs [rems.config/env {:enable-duo true}]
            (testing "unknown code"
              (let [result (api-response :post "/api/resources/create"
                                         {:resid "duo-test-resource"
                                          :organization {:organization/id "organization1"}
                                          :licenses [licid-org1]
                                          :resource/duo {:duo/codes [{:id "DUO:does-not-exist"}]}}
                                         +test-api-key+ user-id)]
                (is (response-is-bad-request? result))))

            (let [result (api-call :post "/api/resources/create"
                                   {:resid "duo-test-resource"
                                    :organization {:organization/id "organization1"}
                                    :licenses [licid-org1]
                                    :resource/duo {:duo/codes [{:id "DUO:0000007" :restrictions [{:type :mondo :values [{:id "MONDO:0000004"}]}]}
                                                               {:id "DUO:0000021"}
                                                               {:id "DUO:0000027" :restrictions [{:type :project :values [{:value "CSC/REMS"}]}]}]}}
                                   +test-api-key+ user-id)
                  id (:id result)]
              (is (:success result))
              (is id)

              (testing "and fetch"
                (let [resource (api-call :get (str "/api/resources/" id) nil
                                         +test-api-key+ user-id)]
                  (is resource)
                  (is (= #{{:id "DUO:0000007"
                            :shorthand "DS"
                            :label {:en "disease specific research"}
                            :description {:en "This data use permission indicates that use is allowed provided it is related to the specified disease."}
                            :restrictions [{:type "mondo"
                                            :values [{:id "MONDO:0000004" :label "adrenocortical insufficiency"}]}]}
                           {:id "DUO:0000021"
                            :shorthand "IRB"
                            :label {:en "ethics approval required"}
                            :description {:en "This data use modifier indicates that the requestor must provide documentation of local IRB/ERB approval."}}
                           {:id "DUO:0000027"
                            :shorthand "PS"
                            :label {:en "project specific restriction"}
                            :description {:en "This data use modifier indicates that use is limited to use within an approved project."}
                            :restrictions [{:type "project" :values [{:value "CSC/REMS"}]}]}}
                         (set (get-in resource [:resource/duo :duo/codes]))))))

              (testing "fetch DUO codes"
                (is (= {:id "DUO:0000007"
                        :shorthand "DS"
                        :label {:en "disease specific research"}
                        :description {:en "This data use permission indicates that use is allowed provided it is related to the specified disease."}
                        :restrictions [{:type "mondo"}]}
                       (first (api-call :get (str "/api/resources/duo-codes") nil +test-api-key+ user-id)))))

              (testing "fetch Mondo codes"
                (is (= {:id "MONDO:0000001", :label "disease or disorder"}
                       (first (api-call :get (str "/api/resources/mondo-codes") nil +test-api-key+ user-id)))))

              (testing "search Mondo codes"
                (is (= {:label "tenosynovitis of foot and ankle" :id "MONDO:0002517"}
                       (first (api-call :get (str "/api/resources/search-mondo-codes?search-text=foo") nil +test-api-key+ user-id))))
                (is (= 100
                       (count (api-call :get (str "/api/resources/search-mondo-codes?search-text=f") nil +test-api-key+ user-id)))
                    "search is limited to maximum 100"))))))

      (testing "with mismatched organizations"
        (let [result (api-call :post "/api/resources/create"
                               {:resid resid :organization {:organization/id "organization1"} :licenses [licid-org1 licid-org2]}
                               +test-api-key+ user-id)]
          (is (true? (:success result))))))

    (testing "create as organization-owner with incorrect organization"
      (let [response (api-response :post "/api/resources/create"
                                   {:resid resid :organization {:organization/id "organization2"} :licenses [licid-org1 licid-org2]}
                                   +test-api-key+ "organization-owner1")]
        (is (response-is-forbidden? response))
        (is (= "no access to organization \"organization2\"" (read-body response)))))))

(deftest resources-api-enable-archive-test
  (let [id (:id (create-resource! {:resid "enable-archive-test"
                                   :organization {:organization/id "organization1"}
                                   :licenses []}
                                  +test-api-key+ "owner"))]
    (is (number? id))
    (doseq [user-id ["owner" "organization-owner1"]]
      (testing user-id
        (testing "disable"
          (is (:success (api-call :put "/api/resources/enabled"
                                  {:id id :enabled false}
                                  +test-api-key+ user-id)))
          (testing "archive"
            (is (:success (api-call :put "/api/resources/archived"
                                    {:id id :archived true}
                                    +test-api-key+ user-id))))
          (testing "fetch"
            (let [res (api-call :get (str "/api/resources/" id) {} +test-api-key+ user-id)]
              (is (false? (:enabled res)))
              (is (true? (:archived res)))))
          (testing "unarchive"
            (is (:success (api-call :put "/api/resources/archived"
                                    {:id id :archived false}
                                    +test-api-key+ user-id))))
          (testing "enable"
            (is (:success (api-call :put "/api/resources/enabled"
                                    {:id id :enabled true}
                                    +test-api-key+ user-id))))
          (testing "fetch again"
            (let [res (api-call :get (str "/api/resources/" id) {} +test-api-key+ user-id)]
              (is (true? (:enabled res)))
              (is (false? (:archived res))))))))
    (testing "as owner of different organization"
      (testing "disable"
        (is (response-is-forbidden? (api-response :put "/api/resources/enabled"
                                                  {:id id :enabled false}
                                                  +test-api-key+ "organization-owner2"))))
      (testing "archive"
        (is (response-is-forbidden? (api-response :put "/api/resources/archived"
                                                  {:id id :archived true}
                                                  +test-api-key+ "organization-owner2")))))
    (testing "handler"
      (testing "disable"
        (is (response-is-forbidden? (api-response :put "/api/resources/enabled"
                                                  {:id id :enabled false}
                                                  +test-api-key+ "handler"))))
      (testing "archive"
        (is (response-is-forbidden? (api-response :put "/api/resources/archived"
                                                  {:id id :archived true}
                                                  +test-api-key+ "handler")))))))

(deftest resources-api-filtering-test
  (let [user-id "owner"
        enabled-id (:id (create-resource! {:resid "enabled"
                                           :organization {:organization/id "organization1"}
                                           :licenses []}
                                          +test-api-key+ user-id))
        _ (resource-enabled! {:id enabled-id :enabled true}
                             +test-api-key+ user-id)
        _ (resource-archived! {:id enabled-id :archived false}
                              +test-api-key+ user-id)
        disabled-id (:id (create-resource! {:resid "disabled"
                                            :organization {:organization/id "organization2"}
                                            :licenses []}
                                           +test-api-key+ user-id))
        _ (resource-enabled! {:id disabled-id :enabled false}
                             +test-api-key+ user-id)
        _ (resource-archived! {:id disabled-id :archived false}
                              +test-api-key+ user-id)
        archived-id (:id (create-resource! {:resid "archived"
                                            :organization {:organization/id "organization1"}
                                            :licenses []}
                                           +test-api-key+ user-id))
        _ (resource-enabled! {:id archived-id :enabled true}
                             +test-api-key+ user-id)
        _ (resource-archived! {:id archived-id :archived true}
                              +test-api-key+ user-id)]
    (testing "hides disabled and archived by default"
      (let [data (-> (request :get "/api/resources")
                     (authenticate +test-api-key+ user-id)
                     handler
                     read-ok-body)
            app-ids (set (map :id data))]
        (is (contains? app-ids enabled-id))
        (is (not (contains? app-ids disabled-id)))
        (is (not (contains? app-ids archived-id)))))
    (testing "includes disabled when requested"
      (let [data (-> (request :get "/api/resources?disabled=true")
                     (authenticate +test-api-key+ user-id)
                     handler
                     read-ok-body)
            app-ids (set (map :id data))]
        (is (contains? app-ids enabled-id))
        (is (contains? app-ids disabled-id))
        (is (not (contains? app-ids archived-id)))))
    (testing "includes archived when requested"
      (let [data (-> (request :get "/api/resources?archived=true")
                     (authenticate +test-api-key+ user-id)
                     handler
                     read-ok-body)
            app-ids (set (map :id data))]
        (is (contains? app-ids enabled-id))
        (is (not (contains? app-ids disabled-id)))
        (is (contains? app-ids archived-id))))
    (testing "can filter by resid"
      (let [other-enabled-id (:id (create-resource! {:resid "special"
                                                     :organization {:organization/id "organization1"}
                                                     :licenses []}
                                                    +test-api-key+ user-id))
            _ (:id (create-resource! {:resid "irrelevant"
                                      :organization {:organization/id "organization1"}
                                      :licenses []}
                                     +test-api-key+ user-id))
            data (-> (request :get "/api/resources?resid=special")
                     (authenticate +test-api-key+ user-id)
                     handler
                     read-ok-body)
            app-ids (set (map :id data))]
        (is (= #{other-enabled-id} app-ids))))))

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

  (test-helpers/create-user! {:userid "alice"})
  (testing "without owner role"
    (let [user-id "alice"]
      (testing "list"
        (let [response (-> (request :get "/api/resources")
                           (authenticate +test-api-key+ user-id)
                           handler)]
          (is (response-is-forbidden? response))
          (is (= "forbidden" (read-body response)))))
      (testing "create"
        (let [response (-> (request :post "/api/resources/create")
                           (authenticate +test-api-key+ user-id)
                           (json-body {:resid "r"
                                       :organization {:organization/id "o"}
                                       :licenses []})
                           handler)]
          (is (response-is-forbidden? response))
          (is (= "forbidden" (read-body response))))))))
