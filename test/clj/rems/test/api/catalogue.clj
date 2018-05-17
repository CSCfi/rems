(ns ^:integration rems.test.api.catalogue
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [ring.mock.request :refer :all]
            [clojure.string :as str]))

(use-fixtures
  :once
  fake-tempura-fixture
  api-fixture)

(deftest catalogue-api-test
  (let [api-key "42"
        user-id "alice"]
    (let [data (-> (request :get "/api/catalogue/")
                   (authenticate api-key user-id)
                   app
                   read-body)
          item (first data)]
      (is (str/starts-with? (:resid item) "http://")))
    (let [data (-> (request :put "/api/catalogue/create")
                   (authenticate api-key "owner")
                   (json-body {:title "test-item-title"
                               :form 1
                               :resid 1
                               :wfid 1})
                   app
                   read-body)]
      (is (= 9 (:id data))))
    (let [data (-> (request :get "/api/catalogue/7")
                   (authenticate api-key user-id)
                   app
                   read-body)]
      (is (= 7 (:id data))))))

(deftest catalogue-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/catalogue"))
                       app)
          body (read-body response)]
      (is (= "unauthorized" body))))
  (testing "item without authentication"
    (let [response (-> (request :get (str "/api/catalogue/2"))
                       app)
          body (read-body response)]
      (is (= "unauthorized" body))))
  (testing "create without authentication"
    (let [response (-> (request :put (str "/api/catalogue/create"))
                       app)
          body (read-body response)]
      (is (str/includes? body "Invalid anti-forgery token"))))
  (testing "create with wrong API-Key"
    (is (= "unauthorized"
           (-> (request :put (str "/api/catalogue/create"))
               (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
               (json-body {:title "malicious item"
                           :form 1
                           :resid 1
                           :wfid 1})
               app
               (read-body)))))
  (testing "create-localization without authentication"
    (let [response (-> (request :put (str "/api/catalogue/create-localization"))
                       (json-body {:id 1
                                   :langcode :fi
                                   :title "malicious localization"})
                       app)
          body (read-body response)]
      (is (str/includes? body "Invalid anti-forgery token"))))
  (testing "create-localization with wrong API-Key"
    (is (= "unauthorized"
           (-> (request :put (str "/api/catalogue/create-localization"))
               (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
               (json-body {:id 1
                           :langcode :fi
                           :title "malicious localization"})
               app
               (read-body))))))
