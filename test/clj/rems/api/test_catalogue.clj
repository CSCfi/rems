(ns ^:integration rems.api.test-catalogue
  (:require [clojure.test :refer :all]
            [rems.service.catalogue :as catalogue]
            [rems.api.testing :refer :all]
            [rems.db.category :as category]
            [rems.service.test-data :as test-data]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.handler :refer [handler]]
            [rems.testing-util :refer [with-user]]
            [rems.text :refer [format-utc-datetime]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :each
  api-fixture)

(deftest test-catalogue-api
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)
  (let [res (test-helpers/create-resource! {:resource-ext-id "urn:1234"})]
    (test-helpers/create-catalogue-item! {:actor "owner" :resource-id res}))
  (let [items (-> (request :get "/api/catalogue/")
                  (authenticate test-data/+test-api-key+ "alice")
                  handler
                  read-ok-body)]
    (is (= ["urn:1234"] (map :resid items)))))

(deftest test-catalogue-api-no-form
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)
  (let [form-id (test-helpers/create-form! {})
        res (test-helpers/create-resource! {:resource-ext-id "urn:5678"})]
    (test-helpers/create-catalogue-item! {:actor "owner" :form-id form-id :resource-id res})
    (test-helpers/create-catalogue-item! {:actor "owner" :form-id nil :resource-id res})
    (is (= #{{:resid "urn:5678" :formid nil}
             {:resid "urn:5678" :formid form-id}}
           (set
            (map #(select-keys % [:resid :formid])
                 (api-call :get "/api/catalogue/" nil test-data/+test-api-key+ "alice")))))))

(deftest test-catalogue-api-security
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)
  (testing "catalogue-is-public true"
    (with-redefs [rems.config/env (assoc rems.config/env :catalogue-is-public true)]
      (is (api-call :get "/api/catalogue" nil nil nil) "should work without authentication")
      (is (api-call :get "/api/catalogue" nil "42" nil) "should work with api key even without a user")
      (is (api-call :get "/api/catalogue" nil "42" "alice") "should work for a regular user")))
  (testing "catalogue-is-public false"
    (with-redefs [rems.config/env (assoc rems.config/env :catalogue-is-public false)]
      (is (api-call :get "/api/catalogue" nil "42" "alice") "should work for a regular user")
      (is (= "unauthorized" (read-body (api-response :get "/api/catalogue" nil nil nil))) "should be unauthorized without authentication")
      (is (= "unauthorized" (read-body (api-response :get "/api/catalogue" nil "invalid-api-key" nil))) "should not work with wrong api key")
      (is (= "unauthorized" (read-body (api-response :get "/api/catalogue" nil "42" nil))) "should not work without a user"))))

(deftest test-get-catalogue-tree
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)

  (let [get-item (fn [id]
                   (-> (catalogue/get-localized-catalogue-item id)
                       (update :start format-utc-datetime)
                       (dissoc :resource-name :form-name :workflow-name)))
        get-category (fn [category]
                       (-> (category/get-category (:category/id category))))
        child {:category/id (test-helpers/create-category! {})}
        parent {:category/id (test-helpers/create-category! {:category/children [child]})}
        _empty {:category/id (test-helpers/create-category! {})} ; should not be seen
        item1 (get-item (test-helpers/create-catalogue-item! {}))
        item2 (get-item (test-helpers/create-catalogue-item! {:enabled false}))
        item3 (get-item (test-helpers/create-catalogue-item! {:categories []}))
        item4 (assoc (get-item (test-helpers/create-catalogue-item! {:categories [child]}))
                     :categories [(get-category child)])
        item5 (get-item (test-helpers/create-catalogue-item! {:categories [] :enabled false}))
        item6 (assoc (get-item (test-helpers/create-catalogue-item! {:categories [child] :enabled false}))
                     :categories [(get-category child)])
        item7 (assoc (get-item (test-helpers/create-catalogue-item! {:categories [parent]}))
                     :categories [(get-category parent)])
        item8 (assoc (get-item (test-helpers/create-catalogue-item! {:categories [parent] :enabled false}))
                     :categories [(get-category parent)])]
    (is (= {:roots [(assoc (get-category parent)
                           :category/children [(assoc (get-category child) :category/items [item4 item6])]
                           :category/items [item7 item8])
                    item1
                    item2
                    item3
                    item5]}
           (api-call :get "/api/catalogue/tree" nil test-data/+test-api-key+ "owner")))

    (testing "a regular user doesn't see disabled"
      (is (= {:roots [(assoc (get-category parent)
                             :category/children [(assoc (get-category child) :category/items [item4])]
                             :category/items [item7])
                      item1
                      item3]}
             (api-call :get "/api/catalogue/tree" nil test-data/+test-api-key+ "alice")))

      (testing "even after disabling more"
        (with-user "owner"
          (catalogue/set-catalogue-item-enabled! {:id (:id item1) :enabled false}) ; top level
          (catalogue/set-catalogue-item-enabled! {:id (:id item4) :enabled false})) ; inside category

        (is (= {:roots [(-> (get-category parent)
                            (assoc :category/items [item7])
                            (dissoc :category/children)) ; child does not have visible items
                        item3]}
               (api-call :get "/api/catalogue/tree" nil test-data/+test-api-key+ "alice")))))))
