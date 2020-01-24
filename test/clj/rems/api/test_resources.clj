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

(deftest resources-api-test
  (let [api-key "42"
        user-id "owner"]
    (testing "get all"
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
            _ (resource-enabled! {:id enabled-id :enabled true}
                                 api-key user-id)
            _ (resource-archived! {:id enabled-id :archived false}
                                  api-key user-id)
            disabled-id (:id (create-resource! {:resid "disabled"
                                                :organization "abc"
                                                :licenses []}
                                               api-key user-id))
            _ (resource-enabled! {:id disabled-id :enabled false}
                                 api-key user-id)
            _ (resource-archived! {:id disabled-id :archived false}
                                  api-key user-id)
            archived-id (:id (create-resource! {:resid "archived"
                                                :organization "abc"
                                                :licenses []}
                                               api-key user-id))
            _ (resource-enabled! {:id archived-id :enabled true}
                                 api-key user-id)
            _ (resource-archived! {:id archived-id :archived true}
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
    (let [licid 1
          resid "resource-api-test"
          create-resource (fn [user-id organization]
                            (-> (request :post "/api/resources/create")
                                (authenticate api-key user-id)
                                (json-body {:resid resid
                                            :organization organization
                                            :licenses [licid]})
                                handler
                                read-ok-body))]
      (testing "create as owner"
        (let [result (create-resource "owner" "test-organization")
              id (:id result)]
          (is (true? (:success result)))
          (is id)

          (testing "and fetch"
            (let [resource (-> (request :get (str "/api/resources/" id))
                               (authenticate api-key user-id)
                               handler
                               assert-response-is-ok
                               read-body)]
              (is resource)
              (is (= [licid] (map :id (:licenses resource))))))

          (testing "duplicate resource ID is not allowed within one organization"
            (let [result (create-resource "owner" "test-organization")]
              (is (false? (:success result)))
              (is (= [{:type "t.administration.errors/duplicate-resid" :resid resid}] (:errors result)))))

          (testing "duplicate resource ID is allowed between organizations"
            (let [result (create-resource "owner" "test-organization2")]
              (is (true? (:success result)))))))

      (testing "create as organization owner"
        (testing "with correct organization"
          (let [result (create-resource "organization-owner" "organization")
                id (:id result)]
            (is (true? (:success result)))
            (is id)))

        (testing "with incorrect organization"
          (let [result (create-resource "organization-owner" "not organization")]
            (is (false? (:success result)))))))))

(deftest resources-api-filtering-test
  (let [api-key "42"
        user-id "owner"
        resources (-> (request :get "/api/resources")
                      (authenticate api-key user-id)
                      handler
                      read-ok-body)
        disabled-id (:id (first resources))
        _ (resource-enabled! {:id disabled-id :enabled false}
                             api-key user-id)
        unfiltered (-> (request :get "/api/resources" {:disabled true})
                       (authenticate api-key user-id)
                       handler
                       read-ok-body)
        filtered (-> (request :get "/api/resources")
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)]
    (is (coll-is-not-empty? unfiltered))
    (is (coll-is-not-empty? filtered))
    (is (not (every? :enabled unfiltered)))
    (is (every? :enabled filtered))
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
          (is (= "unauthorized" (read-body response)))))
      (testing "create"
        (let [response (-> (request :post "/api/resources/create")
                           (authenticate api-key user-id)
                           (json-body {:resid "r"
                                       :organization "o"
                                       :licenses []})
                           handler)]
          (is (response-is-unauthorized? response))
          (is (= "Invalid anti-forgery token" (read-body response)))))))

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
