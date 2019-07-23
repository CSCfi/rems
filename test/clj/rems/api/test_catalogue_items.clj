(ns ^:integration rems.api.test-catalogue-items
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest catalogue-items-api-test
  (let [api-key "42"
        user-id "alice"]
    (let [data (-> (request :get "/api/catalogue-items/")
                   (authenticate api-key user-id)
                   handler
                   read-body)
          item (first data)]
      (is (str/starts-with? (:resid item) "urn:")))
    (let [data (-> (request :post "/api/catalogue-items/create")
                   (authenticate api-key "owner")
                   (json-body {:title "test-item-title"
                               :form 1
                               :resid 1
                               :wfid 1
                               :archived true})
                   handler
                   read-body)
          id (:id data)]
      (is (:success data))
      (is (number? id))
      (let [data (-> (request :get (str "/api/catalogue-items/" id))
                     (authenticate api-key user-id)
                     handler
                     read-body)]
        (is (= {:id id
                :title "test-item-title"
                :workflow-name "dynamic workflow"
                :form-name "Basic form"
                :resource-name "urn:nbn:fi:lb-201403262"}
               (select-keys data [:id :title :workflow-name :form-name :resource-name])))))
    (testing "not found"
      (let [response (-> (request :get "/api/catalogue-items/777777777")
                         (authenticate api-key user-id)
                         handler)]
        (is (response-is-not-found? response))
        (is (= "application/json" (get-in response [:headers "Content-Type"])))))))

(deftest catalogue-items-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/catalogue-items"))
                       handler)
          body (read-body response)]
      (is (response-is-unauthorized? response))
      (is (= "unauthorized" body))))
  (testing "item without authentication"
    (let [response (-> (request :get (str "/api/catalogue-items/2"))
                       handler)
          body (read-body response)]
      (is (response-is-unauthorized? response))
      (is (= "unauthorized" body))))
  (testing "create without authentication"
    (let [response (-> (request :post (str "/api/catalogue-items/create"))
                       handler)
          body (read-body response)]
      (is (response-is-unauthorized? response))
      (is (str/includes? body "Invalid anti-forgery token"))))
  (testing "create with wrong API-Key"
    (is (= "invalid api key"
           (-> (request :post (str "/api/catalogue-items/create"))
               (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
               (json-body {:title "malicious item"
                           :form 1
                           :resid 1
                           :wfid 1})
               handler
               (read-body)))))
  (testing "create-localization without authentication"
    (let [response (-> (request :post (str "/api/catalogue-items/create-localization"))
                       (json-body {:id 1
                                   :langcode :fi
                                   :title "malicious localization"})
                       handler)
          body (read-body response)]
      (is (response-is-unauthorized? response))
      (is (str/includes? body "Invalid anti-forgery token"))))
  (testing "create-localization with wrong API-Key"
    (is (= "invalid api key"
           (-> (request :post (str "/api/catalogue-items/create-localization"))
               (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
               (json-body {:id 1
                           :langcode :fi
                           :title "malicious localization"})
               handler
               (read-body))))))
