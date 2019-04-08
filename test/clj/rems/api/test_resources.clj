(ns ^:integration rems.api.test-resources
  (:require [clojure.test :refer :all]
            [rems.db.core :as db]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(defn- create-resource! [command api-key user-id]
  (-> (request :post "/api/resources/create")
      (authenticate api-key user-id)
      (json-body command)
      handler
      read-ok-body))

(defn- update-resource! [command api-key user-id]
  (-> (request :put "/api/resources/update")
      (authenticate api-key user-id)
      (json-body command)
      handler
      read-ok-body))

(deftest resources-api-test
  (let [api-key "42"
        user-id "owner"]
    (testing "get"
      (testing "returns stuff"
        (let [data (-> (request :get "/api/resources")
                       (authenticate api-key user-id)
                       handler
                       read-ok-body)]
          (is (:id (first data)))))
      (let [enabled-id (:id (create-resource! {:resid "enabled"
                                               :organization "abc"
                                               :licenses []}
                                              api-key user-id))
            _ (update-resource! {:id enabled-id
                                 :enabled true
                                 :archived false}
                                api-key user-id)
            disabled-id (:id (create-resource! {:resid "disabled"
                                                :organization "abc"
                                                :licenses []}
                                               api-key user-id))
            _ (update-resource! {:id disabled-id
                                 :enabled false
                                 :archived false}
                                api-key user-id)
            archived-id (:id (create-resource! {:resid "archived"
                                                :organization "abc"
                                                :licenses []}
                                               api-key user-id))
            _ (update-resource! {:id archived-id
                                 :enabled true
                                 :archived true}
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
    (testing "create"
      (let [licid 1
            resid "RESOURCE-API-TEST"]
        (-> (request :post "/api/resources/create")
            (authenticate api-key user-id)
            (json-body {:resid resid
                        :organization "TEST-ORGANIZATION"
                        :licenses [licid]})
            handler
            assert-response-is-ok)
        (testing "and fetch"
          (let [data (-> (request :get "/api/resources")
                         (authenticate api-key user-id)
                         handler
                         assert-response-is-ok
                         read-body)
                resource (some #(when (= resid (:resid %)) %) data)]
            (is resource)
            (is (= [licid] (map :id (:licenses resource))))))
        (testing "duplicate resource ID is not allowed within one organization"
          (let [response (-> (request :post "/api/resources/create")
                             (authenticate api-key user-id)
                             (json-body {:resid resid
                                         :organization "TEST-ORGANIZATION"
                                         :licenses [licid]})
                             handler)
                body (read-body response)]
            (is (= 200 (:status response)))
            (is (= false (:success body)))
            (is (= [{:type "t.administration.errors/duplicate-resid" :resid resid}] (:errors body)))))
        (testing "duplicate resource ID is allowed between organizations"
          (-> (request :post "/api/resources/create")
              (authenticate api-key user-id)
              (json-body {:resid resid
                          :organization "TEST-ORGANIZATION2"
                          :licenses [licid]})
              handler
              assert-response-is-ok))))))

(deftest resources-api-filtering-test
  (let [unfiltered (-> (request :get "/api/resources" {:expired true})
                       (authenticate "42" "owner")
                       handler
                       assert-response-is-ok
                       read-body)
        filtered (-> (request :get "/api/resources")
                     (authenticate "42" "owner")
                     handler
                     assert-response-is-ok
                     read-body)]
    (is (coll-is-not-empty? unfiltered))
    (is (coll-is-not-empty? filtered))
    (is (every? #(contains? % :expired) unfiltered))
    (is (not-any? :expired filtered))
    (is (< (count filtered) (count unfiltered)))))

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
                                     :organization "o"
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
          (is (= "invalid api key" (read-body response)))))
      (testing "create"
        (let [response (-> (request :post "/api/resources/create")
                           (authenticate api-key user-id)
                           (json-body {:resid "r"
                                       :organization "o"
                                       :licenses []})
                           handler)]
          (is (response-is-unauthorized? response))
          (is (= "invalid api key" (read-body response)))))))

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
                                       :organization "o"
                                       :licenses []})
                           handler)]
          (is (response-is-forbidden? response))
          (is (= "forbidden" (read-body response))))))))
