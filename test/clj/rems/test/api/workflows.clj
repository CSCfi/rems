(ns ^:integration rems.test.api.workflows
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [rems.util :refer [index-by]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
  api-fixture)

(deftest workflows-api-test
  (testing "list"
    (let [response (-> (request :get "/api/workflows")
                       (authenticate "42" "owner")
                       app)
          data (read-body response)
          wfs (index-by [:title] data)
          simple (get wfs "simple")]
      (is (response-is-ok? response))
      (is (coll-is-not-empty? data))
      (is simple)
      (is (= 0 (:final-round simple)))
      (is (= [{:actoruserid "developer"
               :round 0
               :role "approver"}
              {:actoruserid "bob"
               :round 0
               :role "approver"}]
             (:actors simple))))))

(deftest workflows-api-filtering-test
  (let [unfiltered-response (-> (request :get "/api/workflows")
                                (authenticate "42" "owner")
                                app)
        unfiltered-data (read-body unfiltered-response)
        filtered-response (-> (request :get "/api/workflows" {:active true})
                              (authenticate "42" "owner")
                              app)
        filtered-data (read-body filtered-response)]
    (is (response-is-ok? unfiltered-response))
    (is (response-is-ok? filtered-response))
    (is (coll-is-not-empty? unfiltered-data))
    (is (coll-is-not-empty? filtered-data))
    (is (every? #(contains? % :active) unfiltered-data))
    (is (every? :active filtered-data))
    (is (< (count filtered-data) (count unfiltered-data)))))

(deftest workflows-api-security-test
  (testing "without authentication"
    (testing "list"
      (let [response (-> (request :get (str "/api/workflows"))
                         app)]
        (is (= 401 (:status response)))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post (str "/api/workflows/create"))
                         (json-body {:title "workflow title"
                                     :rounds [{:actors [{:userid "bob"
                                                         :role "reviewer"}]}]})
                         app)]
        (is (= 403 (:status response)))
        (is (= "<h1>Invalid anti-forgery token</h1>" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (-> (request :get (str "/api/workflows"))
                         (authenticate "42" "alice")
                         app)]
        (is (= 401 (:status response)))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post (str "/api/workflows/create"))
                         (json-body {:title "workflow title"
                                     :rounds [{:actors [{:userid "bob"
                                                         :role "reviewer"}]}]})
                         (authenticate "42" "alice")
                         app)]
        (is (= 401 (:status response)))
        (is (= "unauthorized" (read-body response)))))))
