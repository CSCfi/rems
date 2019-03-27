(ns ^:integration rems.api.test-workflows
  (:require [clojure.test :refer :all]
            [rems.common-util :refer [index-by]]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures :once api-once-fixture)
(use-fixtures :each api-each-fixture)

(deftest workflows-api-test
  (testing "list"
    (let [data (-> (request :get "/api/workflows")
                   (authenticate "42" "owner")
                   handler
                   assert-response-is-ok
                   read-body)
          wfs (index-by [:title] data)
          simple (get wfs "simple")]
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

  (testing "create auto-approved workflow"
    (let [body (-> (request :post (str "/api/workflows/create"))
                   (json-body {:organization "abc"
                               :title "auto-approved workflow"
                               :type :auto-approve})
                   (authenticate "42" "owner")
                   handler
                   assert-response-is-ok
                   read-body)
          id (:id body)]
      (is (< 0 id))
      (testing "and fetch"
        (let [workflows (-> (request :get "/api/workflows")
                            (authenticate "42" "owner")
                            handler
                            assert-response-is-ok
                            read-body)
              workflow (first (filter #(= id (:id %)) workflows))]
          (is (= {:id id
                  :organization "abc"
                  :title "auto-approved workflow"
                  :final-round 0
                  :actors []}
                 (select-keys workflow [:id :organization :title :final-round :actors])))))))

  (testing "create dynamic workflow"
    (let [body (-> (request :post (str "/api/workflows/create"))
                   (json-body {:organization "abc"
                               :title "dynamic workflow"
                               :type :dynamic
                               :handlers ["bob" "carl"]})
                   (authenticate "42" "owner")
                   handler
                   assert-response-is-ok
                   read-body)
          id (:id body)]
      (is (< 0 id))
      (testing "and fetch"
        (let [workflows (-> (request :get "/api/workflows")
                            (authenticate "42" "owner")
                            handler
                            assert-response-is-ok
                            read-body)
              workflow (first (filter #(= id (:id %)) workflows))]
          (is (= {:id id
                  :organization "abc"
                  :title "dynamic workflow"
                  :workflow {:type "workflow/dynamic"
                             :handlers ["bob" "carl"]}}
                 (select-keys workflow [:id :organization :title :workflow]))))))))

(deftest workflows-api-filtering-test
  (let [unfiltered (-> (request :get "/api/workflows")
                       (authenticate "42" "owner")
                       handler
                       assert-response-is-ok
                       read-body)
        filtered (-> (request :get "/api/workflows" {:active true})
                     (authenticate "42" "owner")
                     handler
                     assert-response-is-ok
                     read-body)]
    (is (coll-is-not-empty? unfiltered))
    (is (coll-is-not-empty? filtered))
    (is (every? #(contains? % :active) unfiltered))
    (is (every? :active filtered))
    (is (< (count filtered) (count unfiltered)))))

(deftest workflows-api-security-test
  (testing "without authentication"
    (testing "list"
      (let [response (-> (request :get (str "/api/workflows"))
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post (str "/api/workflows/create"))
                         (json-body {:organization "abc"
                                     :title "workflow title"
                                     :type :rounds
                                     :rounds [{:type :approval
                                               :actors ["bob"]}]})
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (-> (request :get (str "/api/workflows"))
                         (authenticate "42" "alice")
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post (str "/api/workflows/create"))
                         (json-body {:organization "abc"
                                     :title "workflow title"
                                     :type :rounds
                                     :rounds [{:type :approval
                                               :actors ["bob"]}]})
                         (authenticate "42" "alice")
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))
