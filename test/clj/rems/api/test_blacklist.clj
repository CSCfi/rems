(ns ^:integration rems.api.test-blacklist
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.applications :as applications]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all])
  (:import [org.joda.time DateTimeUtils]))

(use-fixtures
  :once
  api-fixture
  (fn [f]
    (DateTimeUtils/setCurrentMillisFixed 10000)
    (f)
    (DateTimeUtils/setCurrentMillisSystem)))

(def ^:private +fetch-user+ "handler")
(def ^:private +command-user+ "owner")

(defn- fetch [params]
  (-> (request :get "/api/blacklist" params)
      (authenticate "42" +fetch-user+)
      handler
      read-ok-body))

(defn- simplify [blacklist]
  (vec
   (for [entry blacklist]
     {:userid (get-in entry [:blacklist/user :userid])
      :resource/ext-id (get-in entry [:blacklist/resource :resource/ext-id])})))

(defn- add! [command]
  (-> (request :post "/api/blacklist/add")
      (authenticate "42" +command-user+)
      (json-body command)
      handler
      assert-response-is-ok))

(defn- remove! [command]
  (-> (request :post "/api/blacklist/remove")
      (authenticate "42" +command-user+)
      (json-body command)
      handler
      assert-response-is-ok))

(deftest test-blacklist
  (test-helpers/create-user! {:eppn "user1" :email ""})
  (test-helpers/create-user! {:eppn "user2" :email ""})
  (test-helpers/create-user! {:eppn "user3" :email ""})
  (let [res-id-1 (test-helpers/create-resource! {:resource-ext-id "A"})
        res-id-2 (test-helpers/create-resource! {:resource-ext-id "B"})
        res-id-3 (test-helpers/create-resource! {:resource-ext-id "C"})

        cat-id (test-helpers/create-catalogue-item! {:resource-id res-id-2})
        app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id]
                                                  :actor "user2"})
        get-app #(applications/get-application app-id)]
    (testing "initially no blacklist"
      (is (= [] (fetch {})))
      (is (= []
             (:application/blacklist (get-app)))))
    (testing "add three entries"
      (add! {:blacklist/user {:userid "user1"}
             :blacklist/resource {:resource/ext-id "A"}
             :comment "bad"})
      (add! {:blacklist/user {:userid "user1"}
             :blacklist/resource {:resource/ext-id "B"}
             :comment "quite bad"})
      (add! {:blacklist/user {:userid "user2"}
             :blacklist/resource {:resource/ext-id "B"}
             :comment "very bad"})
      (is (= [{:blacklist/resource {:resource/ext-id "A"}
               :blacklist/user {:userid "user1" :name nil :email nil}
               :blacklist/added-by {:userid "owner" :name "Owner" :email "owner@example.com"}
               :blacklist/added-at "1970-01-01T00:00:10.000Z"
               :blacklist/comment "bad"}
              {:blacklist/resource {:resource/ext-id "B"}
               :blacklist/user {:userid "user1" :name nil :email nil}
               :blacklist/added-by {:userid "owner" :name "Owner" :email "owner@example.com"}
               :blacklist/added-at "1970-01-01T00:00:10.000Z"
               :blacklist/comment "quite bad"}
              {:blacklist/resource {:resource/ext-id "B"}
               :blacklist/user {:userid "user2" :name nil :email nil}
               :blacklist/added-by {:userid "owner" :name "Owner" :email "owner@example.com"}
               :blacklist/added-at "1970-01-01T00:00:10.000Z"
               :blacklist/comment "very bad"}]
             (fetch {}))))
    (testing "application is updated when user is added to blacklist"
      (is (= [{:blacklist/user {:userid "user2" :name nil :email nil}
               :blacklist/resource {:resource/ext-id "B"}}]
             (:application/blacklist (get-app)))))
    (testing "query parameters"
      (is (= [{:resource/ext-id "A" :userid "user1"}
              {:resource/ext-id "B" :userid "user1"}]
             (simplify (fetch {:user "user1"}))))
      (is (= [{:resource/ext-id "B" :userid "user1"}
              {:resource/ext-id "B" :userid "user2"}]
             (simplify (fetch {:resource "B"}))))
      (is (= [{:resource/ext-id "B" :userid "user2"}]
             (simplify (fetch {:resource "B" :user "user2"})))))
    (testing "remove entry"
      (remove! {:blacklist/user {:userid "user2"}
                :blacklist/resource {:resource/ext-id "B"}
                :comment "oops"})
      (is (= []
             (fetch {:resource "B" :user "user2"}))))
    (testing "application is updated when user is removed from blacklist"
      (is (= []
             (:application/blacklist (get-app)))))
    (testing "add entry again"
      (add! {:blacklist/user {:userid "user2"}
             :blacklist/resource {:resource/ext-id "B"}
             :comment "again"})
      (is (= [{:resource/ext-id "B" :userid "user2"}]
             (simplify (fetch {:resource "B" :user "user2"})))))
    (testing "remove nonexistent entry"
      (remove! {:blacklist/user {:userid "user3"}
                :blacklist/resource {:resource/ext-id "C"}
                :comment "undo"})
      (is (= [{:resource/ext-id "A" :userid "user1"}
              {:resource/ext-id "B" :userid "user1"}
              {:resource/ext-id "B" :userid "user2"}]
             (simplify (fetch {})))))))
