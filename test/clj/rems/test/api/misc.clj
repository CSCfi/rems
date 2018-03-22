(ns ^:integration rems.test.api.misc
  (:require [cheshire.core :refer [generate-string parse-stream]]
            [clojure.string :refer [starts-with?]]
            [clojure.test :refer :all]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]
            [rems.handler :refer :all]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
  api-fixture)

(deftest service-catalogue-test
  (let [api-key "42"
        user-id "alice"]
    (let [data (-> (request :get "/api/catalogue/")
                   (authenticate api-key user-id)
                   app
                   read-body)
          item (first data)]
      (is (starts-with? (:resid item) "http://")))
    (let [data (-> (request :put "/api/catalogue/create")
                   (authenticate api-key user-id)
                   (json {:title "test-item-title"
                          :form 1
                          :resid 1
                          :wfid 1})
                   app
                   read-body)]
      (is (= 7 (:id data))))
    (let [data (-> (request :get "/api/catalogue/7")
                   (authenticate api-key user-id)
                   app
                   read-body)]
      (is (= 7 (:id data))))))

(deftest service-applications-test
  (let [api-key "42"
        user-id "developer"]
    (let [data (-> (request :get "/api/applications")
                   (authenticate api-key user-id)
                   app
                   read-body)]
      (is (= [1 2 3 4 5 6 7] (map :id (sort-by :id data)))))))

(deftest service-actions-test
  (let [api-key "42"
        user-id "developer"]
    (let [data (-> (request :get "/api/actions")
                   (authenticate api-key user-id)
                   app
                   read-body)]
      (is (= [2 2 3] (sort (map :id (mapcat :catalogue-items (:approvals data)))))))))

(deftest service-translations-test
  (let [api-key "42"
        user-id "alice"]
    (let [data (-> (request :get "/api/translations")
                   (authenticate api-key user-id)
                   app
                   read-body)
          languages (keys data)]
      (is (= [:en :en-GB :fi] (sort languages))))))
