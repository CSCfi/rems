(ns ^:integration rems.test.api.workflows
  (:require [clojure.test :refer :all]
            [rems.common-util :refer [index-by]]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
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
             (:actors simple)))))
  (testing "create"
    (let [response (-> (request :post (str "/api/workflows/create"))
                       (json-body {:organization "abc"
                                   :title "workflow title"
                                   :rounds [{:type :review
                                             :actors [{:userid "alice"}
                                                      {:userid "bob"}]}
                                            {:type :approval
                                             :actors [{:userid "carl"}]}]})
                       (authenticate "42" "owner")
                       app)
          body (read-body response)
          id (:id body)]
      (is (response-is-ok? response))
      (is (< 0 id))
      (testing "and fetch"
        (let [response (-> (request :get "/api/workflows")
                           (authenticate "42" "owner")
                           app)
              workflows (read-body response)
              workflow (first (filter #(= id (:id %)) workflows))]
          (is (response-is-ok? response))
          (is (= {:id id
                  :organization "abc"
                  :title "workflow title"
                  :final-round 1
                  :actors [{:actoruserid "alice", :role "reviewer", :round 0}
                           {:actoruserid "bob", :role "reviewer", :round 0}
                           {:actoruserid "carl", :role "approver", :round 1}]}
                 (select-keys workflow [:id :organization :title :final-round :actors])))))))
  (testing "create auto-approved workflow"
    (let [response (-> (request :post (str "/api/workflows/create"))
                       (json-body {:organization "abc"
                                   :title "auto-approved workflow"
                                   :rounds []})
                       (authenticate "42" "owner")
                       app)
          body (read-body response)
          id (:id body)]
      (is (response-is-ok? response))
      (is (< 0 id))
      (testing "and fetch"
        (let [response (-> (request :get "/api/workflows")
                           (authenticate "42" "owner")
                           app)
              workflows (read-body response)
              workflow (first (filter #(= id (:id %)) workflows))]
          (is (response-is-ok? response))
          (is (= {:id id
                  :organization "abc"
                  :title "auto-approved workflow"
                  :final-round 0
                  :actors []}
                 (select-keys workflow [:id :organization :title :final-round :actors]))))))))

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
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post (str "/api/workflows/create"))
                         (json-body {:organization "abc"
                                     :title "workflow title"
                                     :rounds [{:type :approval
                                               :actors [{:userid "bob"}]}]})
                         app)]
        (is (response-is-forbidden? response))
        (is (= "<h1>Invalid anti-forgery token</h1>" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (-> (request :get (str "/api/workflows"))
                         (authenticate "42" "alice")
                         app)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post (str "/api/workflows/create"))
                         (json-body {:organization "abc"
                                     :title "workflow title"
                                     :rounds [{:type :approval
                                               :actors [{:userid "bob"}]}]})
                         (authenticate "42" "alice")
                         app)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))))
