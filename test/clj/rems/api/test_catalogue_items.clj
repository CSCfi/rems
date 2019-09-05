(ns ^:integration rems.api.test-catalogue-items
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.test-data :as test-data]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest catalogue-items-api-test
  (let [api-key "42"
        user-id "alice"
        form-id (test-data/create-form! {:form/title "form name"})]
    (let [data (-> (request :get "/api/catalogue-items/")
                   (authenticate api-key user-id)
                   handler
                   read-body)
          item (first data)]
      (is (str/starts-with? (:resid item) "urn:")))
    (let [data (-> (request :post "/api/catalogue-items/create")
                   (authenticate api-key "owner")
                   (json-body {:form form-id
                               :resid 1
                               :wfid 1
                               :archived true
                               :localizations {}})
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
                :workflow-name "dynamic workflow"
                :form-name "form name"
                :resource-name "urn:nbn:fi:lb-201403262"
                :localizations {}}
               (select-keys data [:id :workflow-name :form-name :resource-name :localizations])))))
    (testing "not found"
      (let [response (-> (request :get "/api/catalogue-items/777777777")
                         (authenticate api-key user-id)
                         handler)]
        (is (response-is-not-found? response))
        (is (= "application/json" (get-in response [:headers "Content-Type"])))))))

(deftest catalogue-items-edit-test
  (let [form-id (test-data/create-form! {:form/title "form name"})
        api-key "42"
        owner "owner"
        user "alice"]
    (testing "create"
      (let [create (-> (request :post "/api/catalogue-items/create")
                       (authenticate api-key owner)
                       (json-body {:form form-id
                                   :resid 1
                                   :wfid 1
                                   :localizations {:en {:title "En title"}
                                                   :sv {:title "Sv title"
                                                        :infourl "http://info.se"}}})
                       handler
                       read-ok-body)
            id (:id create)]
        (is (:success create))
        (testing "... and fetch"
          (let [data (-> (request :get (str "/api/catalogue-items/" id))
                         (authenticate api-key user)
                         handler
                         read-ok-body)]
            (is (= id (:id data)))
            (is (= {:title "En title"
                    :infourl nil}
                   (dissoc (get-in data [:localizations :en]) :id :langcode)))
            (is (= {:title "Sv title"
                    :infourl "http://info.se"}
                   (dissoc (get-in data [:localizations :sv]) :id :langcode)))))
        (testing "... and edit"
          (let [response (-> (request :put "/api/catalogue-items/edit")
                             (authenticate api-key owner)
                             (json-body {:id id
                                         :localizations {:sv {:title "Sv title 2"
                                                              :infourl nil}
                                                         :fi {:title "Fi title"
                                                              :infourl "http://info.fi"}}})
                             handler
                             read-ok-body)]
            (is (:success response) (pr-str response))
            (testing "... and fetch"
              (let [data (-> (request :get (str "/api/catalogue-items/" id))
                             (authenticate api-key user)
                             handler
                             read-ok-body)]
                (prn data)
                (is (= id (:id data)))
                (is (= {:title "En title"
                        :infourl nil}
                       (dissoc (get-in data [:localizations :en]) :id :langcode)))
                (is (= {:title "Sv title 2"
                        :infourl nil}
                       (dissoc (get-in data [:localizations :sv]) :id :langcode)))
                (is (= {:title "Fi title"
                        :infourl "http://info.fi"}
                       (dissoc (get-in data [:localizations :fi]) :id :langcode)))))))))))


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
               (json-body {:form 1
                           :resid 1
                           :wfid 1
                           :localizations {}})
               handler
               (read-body)))))
  (testing "edit without authentication"
    (let [response (-> (request :post (str "/api/catalogue-items/edit"))
                       (json-body {:id 1
                                   :localizations {:en {:title "malicious localization"}}})
                       handler)
          body (read-body response)]
      (is (response-is-unauthorized? response))
      (is (str/includes? body "Invalid anti-forgery token"))))
  (testing "edit with wrong API-Key"
    (is (= "invalid api key"
           (-> (request :post (str "/api/catalogue-items/edit"))
               (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
               (json-body {:id 1
                           :localizations {:en {:title "malicious localization"}}})
               handler
               (read-body))))))
