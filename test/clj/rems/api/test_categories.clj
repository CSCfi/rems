(ns ^:integration rems.api.test-categories
  (:require [clojure.test :refer :all]
            [rems.db.testing :refer [owners-fixture +test-api-key+]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]
            [rems.db.test-data-helpers :as test-helpers]))

(use-fixtures
  :each
  api-fixture
  owners-fixture)

(deftest categories-api-create-test
  (let [owner "owner"
        org-owner "organization-owner1"
        create-category-data {:category/title {:fi "integraatiotesti"
                                               :sv "integrationstest"
                                               :en "integration test"}
                              :category/display-order 10000000 ; will be capped to max value
                              :category/description {:fi "integraatiotesti"
                                                     :sv "integrationstest"
                                                     :en "integration test"}
                              :category/children []}]

    (doseq [user-id [owner org-owner]]
      (testing "fetch nonexistent"
        (let [response (api-response :get "/api/categories/9999999" nil
                                     +test-api-key+ user-id)]
          (is (response-is-not-found? response))
          (is (= {:error "not found"} (read-body response)))))

      (testing "create"
        (let [category (api-call :post "/api/categories/create"
                                 create-category-data
                                 +test-api-key+ user-id)]
          (is (:success category))
          (is (int? (:category/id category)))

          (testing "and fetch"
            (let [result (api-call :get (str "/api/categories/" (:category/id category)) nil
                                   +test-api-key+ user-id)
                  expected (merge create-category-data
                                  {:category/id (:category/id category)
                                   :category/display-order 1000000})]
              (is (= expected result))))))

      (testing "adding category as children"
        (let [dep-category (api-call :post "/api/categories/create"
                                     create-category-data
                                     +test-api-key+ user-id)]
          (is (:success dep-category))
          (is (int? (:category/id dep-category)))

          (let [category (api-call :post "/api/categories/create"
                                   (merge create-category-data
                                          {:category/children [{:category/id (:category/id dep-category)}]})
                                   +test-api-key+ user-id)]
            (is (:success category))
            (is (int? (:category/id category)))

            (let [result (api-call :get (str "/api/categories/" (:category/id category)) nil
                                   +test-api-key+ user-id)
                  expected (merge create-category-data
                                  {:category/id (:category/id category)
                                   :category/display-order 1000000
                                   :category/children [(merge {:category/id (:category/id dep-category)
                                                               :category/display-order 1000000}
                                                              (select-keys create-category-data
                                                                           [:category/title :category/description :category/children]))]})]
              (is (= expected result))))))

      (testing "creating category with non-existing children should fail"
        (let [result (api-call :post "/api/categories/create"
                               (merge create-category-data
                                      {:category/children [{:category/id 9999999}]})
                               +test-api-key+ user-id)]
          (is (not (:success result)))
          (is (= [{:type "t.administration.errors/dependencies-not-found"
                   :categories [{:category/id 9999999}]}]
                 (:errors result))))))))

(deftest categories-api-update-test
  (let [owner "owner"
        org-owner "organization-owner1"
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
    (doseq [user-id [owner org-owner]]
      (testing "create"
        (let [category (api-call :post "/api/categories/create"
                                 create-category-data
                                 +test-api-key+ user-id)]
          (is (:success category))
          (is (int? (:category/id category)))

          (testing "and update"
            (let [update-result (api-call :put "/api/categories/edit"
                                          (merge create-category-data
                                                 update-category-data
                                                 {:category/id (:category/id category)})
                                          +test-api-key+ user-id)]
              (is (:success update-result))

              (let [result (api-call :get (str "/api/categories/" (:category/id category)) nil
                                     +test-api-key+ user-id)
                    expected (merge create-category-data
                                    update-category-data
                                    {:category/id (:category/id category)})]
                (is (= expected result)))))

          (testing "updating category with self as child should fail"
            (let [result (api-call :put "/api/categories/edit"
                                   (merge create-category-data
                                          update-category-data
                                          {:category/id (:category/id category)
                                           :category/children [{:category/id (:category/id category)}]})
                                   +test-api-key+ user-id)]
              (is (not (:success result)))
              (is (= [{:type "t.administration.errors/self-as-subcategory-disallowed"
                       :category/id (:category/id category)}]
                     (:errors result)))))

          (testing "should error when setting ancestor categories as category children"
            (let [ancestor-category (api-call :post "/api/categories/create"
                                              (merge create-category-data
                                                     {:category/children [{:category/id (:category/id category)}]})
                                              +test-api-key+ user-id)
                  subcategory (api-call :post "/api/categories/create"
                                        create-category-data
                                        +test-api-key+ user-id)
                  update-parent-result (api-call :put "/api/categories/edit"
                                                 (merge create-category-data
                                                        {:category/id (:category/id category)
                                                         :category/children [{:category/id (:category/id subcategory)}]})
                                                 +test-api-key+ user-id)
                  loop-update-result (api-call :put "/api/categories/edit"
                                               (merge create-category-data
                                                      {:category/id (:category/id subcategory)
                                                       :category/children [{:category/id (:category/id ancestor-category)}
                                                                           {:category/id (:category/id category)}]})
                                               +test-api-key+ user-id)]
              (is (:success update-parent-result))
              (is (not (:success loop-update-result)))
              (is (= [{:type "t.administration.errors/ancestor-as-subcategory-disallowed"
                       :categories [{:category/id (:category/id ancestor-category)
                                     :category/title (:category/title create-category-data)}
                                    {:category/id (:category/id category)
                                     :category/title (:category/title create-category-data)}]}]
                     (:errors loop-update-result)))))))

      (testing "updating non-existing category returns 404"
        (let [response (api-response :put "/api/categories/edit"
                                     (merge create-category-data
                                            update-category-data
                                            {:category/id 9999999})
                                     +test-api-key+ user-id)]
          (is (not (:success response)))
          (is (= {:error "not found"} (read-body response))))))))

(deftest categories-api-delete-test
  (let [owner "owner"
        org-owner "organization-owner1"
        create-category-data {:category/title {:fi "integraatiotesti"
                                               :sv "integrationstest"
                                               :en "integration test"}
                              :category/description {:fi "integraatiotesti"
                                                     :sv "integrationstest"
                                                     :en "integration test"}
                              :category/children []}]

    (doseq [user-id [owner org-owner]]
      (testing "create"
        (let [dep-category (api-call :post "/api/categories/create"
                                     create-category-data
                                     +test-api-key+ user-id)
              category (api-call :post "/api/categories/create"
                                 (merge create-category-data
                                        {:category/children [{:category/id (:category/id dep-category)}]})
                                 +test-api-key+ user-id)]
          (is (:success dep-category))
          (is (:success category))
          (is (int? (:category/id dep-category)))
          (is (int? (:category/id category)))

          (testing "and delete"
            (let [result (api-call :post "/api/categories/delete"
                                   {:category/id (:category/id category)}
                                   +test-api-key+ user-id)]
              (is (:success result))

              (let [response (api-response :get (str "/api/categories/" (:category/id category)) nil
                                           +test-api-key+ user-id)]
                (is (response-is-not-found? response))
                (is (= {:error "not found"} (read-body response))))))))

      (testing "cannot delete category that is depended on by another category"
        (let [dep-category (api-call :post "/api/categories/create"
                                     create-category-data
                                     +test-api-key+ user-id)]
          (is (:success dep-category))
          (is (int? (:category/id dep-category)))

          (let [category (api-call :post "/api/categories/create"
                                   (merge create-category-data
                                          {:category/children [{:category/id (:category/id dep-category)}]})
                                   +test-api-key+ user-id)]
            (is (:success category))
            (is (int? (:category/id category)))

            (let [result (api-call :post "/api/categories/delete"
                                   {:category/id (:category/id dep-category)}
                                   +test-api-key+ user-id)]
              (is (not (:success result)))
              (is (= [{:type "t.administration.errors/in-use-by"
                       :categories [{:category/id (:category/id category)
                                     :category/title (:category/title create-category-data)}]}]
                     (:errors result)))))))

      (testing "cannot delete category that is depended on by a catalogue item"
        (let [dep-category (api-call :post "/api/categories/create"
                                     create-category-data
                                     +test-api-key+ user-id)]
          (is (:success dep-category))
          (is (int? (:category/id dep-category)))

          (let [resource (test-helpers/create-resource! {:resource-ext-id "urn:1234"})
                catalogue-item (test-helpers/create-catalogue-item! {:actor user-id
                                                                     :resource-id resource
                                                                     :categories [(select-keys dep-category [:category/id])]})]
            (is (int? catalogue-item))

            (let [result (api-call :post "/api/categories/delete"
                                   {:category/id (:category/id dep-category)}
                                   +test-api-key+ user-id)]
              (is (not (:success result)))
              (is (= [{:type "t.administration.errors/in-use-by"
                       :catalogue-items [{:id catalogue-item :localizations {}}]}]
                     (:errors result)))))))

      (testing "deleting non-existing category returns 404"
        (let [response (api-response :post "/api/categories/delete"
                                     {:category/id 9999999}
                                     +test-api-key+ user-id)]
          (is (not (:success response)))
          (is (= {:error "not found"} (read-body response))))))))
