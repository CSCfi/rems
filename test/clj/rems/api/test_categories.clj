(ns ^:integration rems.api.test-categories
  (:require [clojure.test :refer :all]
            [rems.db.testing :refer [owners-fixture +test-api-key+]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :each
  api-fixture
  owners-fixture)

(deftest categories-api-create-test
  (let [owner "owner"
        org-owner "organization-owner1"
        create-category-data {:organization {:organization/id "organization1"}
                              :category/title {:fi "integraatiotesti"
                                               :sv "integrationstest"
                                               :en "integration test"}
                              :category/description {:fi "integraatiotesti"
                                                     :sv "integrationstest"
                                                     :en "integration test"}
                              :category/children []}
        join-organization {:organization {:organization/id "organization1"
                                          :organization/short-name {:en "ORG 1"
                                                                    :fi "ORG 1"
                                                                    :sv "ORG 1"}
                                          :organization/name {:en "Organization 1"
                                                              :fi "Organization 1"
                                                              :sv "Organization 1"}}}]

    (testing "fetch nonexistent"
      (let [response (api-response :get "/api/categories/9999999" nil
                                   +test-api-key+ owner)]
        (is (response-is-not-found? response))
        (is (= {:error "not found"} (read-body response)))))

    (doseq [user-id [owner org-owner]]
      (testing "create"
        (let [result (api-call :post "/api/categories"
                               create-category-data
                               +test-api-key+ user-id)
              id (:id result)]
          (is (true? (:success result)))
          (is (int? id))

          (testing "and fetch"
            (let [category (api-call :get (str "/api/categories/" id) nil
                                     +test-api-key+ user-id)
                  expected (merge create-category-data
                                  join-organization
                                  {:id id})]
              (is (= category expected)))))))

    (testing "adding category as children"
      (let [owner "owner"
            result (api-call :post "/api/categories"
                             create-category-data
                             +test-api-key+ owner)
            id-1 (:id result)]
        (is (true? (:success result)))
        (is (int? id-1))

        (let [result-2 (api-call :post "/api/categories"
                                 (merge create-category-data
                                        {:category/children [{:category/id id-1}]})
                                 +test-api-key+ owner)
              id-2 (:id result-2)]
          (is (true? (:success result-2)))
          (is (int? id-2))

          (let [category (api-call :get (str "/api/categories/" id-2) nil
                                   +test-api-key+ owner)
                expected (merge create-category-data
                                join-organization
                                {:id id-2
                                 :category/children [{:category/id id-1
                                                      :category/title (get create-category-data :category/title)}]})]
            (is (= category expected))))))

    (testing "enrich unknown categories"
      (let [owner "owner"
            result (api-call :post "/api/categories"
                             (merge create-category-data
                                    {:category/children [{:category/id 9999999}]})
                             +test-api-key+ owner)
            id (:id result)]
        (is (true? (:success result)))
        (is (int? id))

        (let [category (api-call :get (str "/api/categories/" id) nil
                                 +test-api-key+ owner)
              expected (merge create-category-data
                              join-organization
                              {:id id
                               :category/children [{:category/id 9999999
                                                    :category/title {:fi "Tuntematon kategoria"
                                                                     :sv "Okänd kategori"
                                                                     :en "Unknown category"}}]})]
          (is (= category expected)))))))

(deftest categories-api-update-test
  (let [owner "owner"
        org-owner "organization-owner1"
        create-category-data {:organization {:organization/id "organization1"}
                              :category/title {:fi "integraatiotesti"
                                               :sv "integrationstest"
                                               :en "integration test"}
                              :category/description {:fi "integraatiotesti"
                                                     :sv "integrationstest"
                                                     :en "integration test"}
                              :category/children []}
        update-category-data {:category/title {:fi "integraatiotesti 2"
                                               :sv "integrationstest 2"
                                               :en "integration test 2"}}
        join-organization {:organization {:organization/id "organization1"
                                          :organization/short-name {:en "ORG 1"
                                                                    :fi "ORG 1"
                                                                    :sv "ORG 1"}
                                          :organization/name {:en "Organization 1"
                                                              :fi "Organization 1"
                                                              :sv "Organization 1"}}}]

    (doseq [user-id [owner org-owner]]
      (testing "create"
        (let [result (api-call :post "/api/categories"
                               create-category-data
                               +test-api-key+ user-id)
              id (:id result)]
          (is (true? (:success result)))
          (is (int? id))

          (testing "and update"
            (let [result (api-call :put "/api/categories"
                                   (merge create-category-data
                                          update-category-data
                                          {:category/id id})
                                   +test-api-key+ user-id)]
              (is (true? (:success result)))
              (is (int? id))

              (let [category (api-call :get (str "/api/categories/" id) nil
                                       +test-api-key+ user-id)
                    expected (merge create-category-data
                                    update-category-data
                                    join-organization
                                    {:id id})]
                (is (= category expected))))))))))

(deftest categories-api-delete-test
  (let [owner "owner"
        org-owner "organization-owner1"
        create-category-data {:organization {:organization/id "organization1"}
                              :category/title {:fi "integraatiotesti"
                                               :sv "integrationstest"
                                               :en "integration test"}
                              :category/description {:fi "integraatiotesti"
                                                     :sv "integrationstest"
                                                     :en "integration test"}
                              :category/children []}]

    (doseq [user-id [owner org-owner]]
      (testing "create"
        (let [result (api-call :post "/api/categories"
                               create-category-data
                               +test-api-key+ user-id)
              id (:id result)]
          (is (true? (:success result)))
          (is (int? id))

          (testing "and delete"
            (let [result (api-call :post "/api/categories/remove"
                                   {:category/id id}
                                   +test-api-key+ user-id)]
              (is (true? (:success result)))

              (let [response (api-response :get (str "/api/categories/" id) nil
                                           +test-api-key+ user-id)]
                (is (response-is-not-found? response))
                (is (= {:error "not found"} (read-body response)))))))))))