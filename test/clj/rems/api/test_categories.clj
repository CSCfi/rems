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
        create-category-data {:category/title {:fi "integraatiotesti"
                                               :sv "integrationstest"
                                               :en "integration test"}
                              :category/description {:fi "integraatiotesti"
                                                     :sv "integrationstest"
                                                     :en "integration test"}
                              :category/children []}]

    (testing "fetch nonexistent"
      (let [response (api-response :get "/api/categories/9999999" nil
                                   +test-api-key+ owner)]
        (is (response-is-not-found? response))
        (is (= {:error "not found"} (read-body response)))))

    (testing "create"
      (let [category (api-call :post "/api/categories"
                               create-category-data
                               +test-api-key+ owner)]
        (is (:success category))
        (is (int? (:id category)))

        (testing "and fetch"
          (let [result (api-call :get (str "/api/categories/" (:id category)) nil
                                   +test-api-key+ owner)
                expected (merge create-category-data
                                {:id (:id category)})]
            (is (= result expected))))))

    (testing "adding category as children"
      (let [owner "owner"
            dep-category (api-call :post "/api/categories"
                                   create-category-data
                                   +test-api-key+ owner)]
        (is (:success dep-category))
        (is (int? (:id dep-category)))

        (let [category (api-call :post "/api/categories"
                                 (merge create-category-data
                                        {:category/children [{:category/id (:id dep-category)}]})
                                 +test-api-key+ owner)]
          (is (:success category))
          (is (int? (:id category)))

          (let [result (api-call :get (str "/api/categories/" (:id category)) nil
                                   +test-api-key+ owner)
                expected (merge create-category-data
                                {:id (:id category)
                                 :category/children [{:category/id (:id dep-category)
                                                      :category/title (get create-category-data :category/title)}]})]
            (is (= result expected))))))

    (testing "creating category with non-existing children should fail"
      (let [owner "owner"
            result (api-call :post "/api/categories"
                             (merge create-category-data
                                    {:category/children [{:category/id 9999999}]})
                             +test-api-key+ owner)]
        (is (not (:success result)))
        (is (= (:errors result)
               [{:type "t.administration.errors/dependencies-not-found"
                 :categories [{:category/id 9999999}]}]))))))

(deftest categories-api-update-test
  (let [owner "owner"
        create-category-data {:category/title {:fi "integraatiotesti"
                                               :sv "integrationstest"
                                               :en "integration test"}
                              :category/description {:fi "integraatiotesti"
                                                     :sv "integrationstest"
                                                     :en "integration test"}
                              :category/children []}
        update-category-data {:category/title {:fi "integraatiotesti 2"
                                               :sv "integrationstest 2"
                                               :en "integration test 2"}}]

    (testing "create"
      (let [category (api-call :post "/api/categories"
                               create-category-data
                               +test-api-key+ owner)]
        (is (:success category))
        (is (int? (:id category)))

        (testing "and update"
          (let [update-result (api-call :put "/api/categories"
                                  (merge create-category-data
                                         update-category-data
                                         {:category/id (:id category)})
                                  +test-api-key+ owner)]
            (is (:success update-result))

            (let [result (api-call :get (str "/api/categories/" (:id category)) nil
                                   +test-api-key+ owner)
                  expected (merge create-category-data
                                  update-category-data
                                  {:id (:id category)})]
              (is (= result expected)))))))))

(deftest categories-api-delete-test
  (let [owner "owner"
        create-category-data {:category/title {:fi "integraatiotesti"
                                               :sv "integrationstest"
                                               :en "integration test"}
                              :category/description {:fi "integraatiotesti"
                                                     :sv "integrationstest"
                                                     :en "integration test"}
                              :category/children []}]

    (testing "create"
      (let [dep-category (api-call :post "/api/categories"
                                   create-category-data
                                   +test-api-key+ owner)
            category (api-call :post "/api/categories"
                               (merge create-category-data
                                      {:category/children [{:category/id (:id dep-category)}]})
                               +test-api-key+ owner)]
        (is (:success dep-category))
        (is (:success category))
        (is (int? (:id dep-category)))
        (is (int? (:id category)))

        (testing "and delete"
          (let [result (api-call :post "/api/categories/remove"
                                 {:category/id (:id category)}
                                 +test-api-key+ owner)]
            (is (:success result))

            (let [response (api-response :get (str "/api/categories/" (:id category)) nil
                                         +test-api-key+ owner)]
              (is (response-is-not-found? response))
              (is (= {:error "not found"} (read-body response))))))))

    (testing "cannot delete category that is depended on by another category"
      (let [dep-category (api-call :post "/api/categories"
                                   create-category-data
                                   +test-api-key+ owner)]
        (is (:success dep-category))
        (is (int? (:id dep-category)))

        (let [category (api-call :post "/api/categories"
                                 (merge create-category-data
                                        {:category/children [{:category/id (:id dep-category)}]})
                                 +test-api-key+ owner)]
          (is (:success category))
          (is (int? (:id category)))

          (let [result (api-call :post "/api/categories/remove"
                                 {:category/id (:id dep-category)}
                                 +test-api-key+ owner)]
            (is (not (:success result)))
            (is (= (:errors result)
                   [{:type "t.administration.errors/in-use-by"
                     :categories [{:id (:id category)
                                   :category/title (:category/title create-category-data)}]}]))))))))