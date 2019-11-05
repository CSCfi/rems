(ns ^:integration rems.api.test-blacklist
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(def ^:private +fetch-user+ "handler")
(def ^:private +command-user+ "owner")

(defn- fetch [params]
  (-> (request :get "/api/blacklist" params)
      (authenticate "42" +fetch-user+)
      handler
      read-ok-body
      set))

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
  (testing "initially no blacklist"
    (is (= #{} (fetch {}))))
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
    (is (= #{{:blacklist/resource {:resource/ext-id "A"} :blacklist/user {:userid "user1" :name nil :email nil}}
             {:blacklist/resource {:resource/ext-id "B"} :blacklist/user {:userid "user1" :name nil :email nil}}
             {:blacklist/resource {:resource/ext-id "B"} :blacklist/user {:userid "user2" :name nil :email nil}}}
           (fetch {}))))
  (testing "query parameters"
    (is (= #{{:blacklist/resource {:resource/ext-id "A"} :blacklist/user {:userid "user1" :name nil :email nil}}
             {:blacklist/resource {:resource/ext-id "B"} :blacklist/user {:userid "user1" :name nil :email nil}}}
           (fetch {:user "user1"})))
    (is (= #{{:blacklist/resource {:resource/ext-id "B"} :blacklist/user {:userid "user1" :name nil :email nil}}
             {:blacklist/resource {:resource/ext-id "B"} :blacklist/user {:userid "user2" :name nil :email nil}}}
           (fetch {:resource "B"})))
    (is (= #{{:blacklist/resource {:resource/ext-id "B"} :blacklist/user {:userid "user2" :name nil :email nil}}}
           (fetch {:resource "B" :user "user2"}))))
  (testing "remove entry"
    (remove! {:blacklist/user {:userid "user2"}
              :blacklist/resource {:resource/ext-id "B"}
              :comment "oops"})
    (is (= #{}
           (fetch {:resource "B" :user "user2"}))))
  (testing "add entry again"
    (add! {:blacklist/user {:userid "user2"}
           :blacklist/resource {:resource/ext-id "B"}
           :comment "again"})
    (is (= #{{:blacklist/resource {:resource/ext-id "B"} :blacklist/user {:userid "user2" :name nil :email nil}}}
           (fetch {:resource "B" :user "user2"}))))
  (testing "remove nonexistent entry"
    (remove! {:blacklist/user {:userid "user3"}
              :blacklist/resource {:resource/ext-id "C"}
              :comment "undo"})
    (is (= #{{:blacklist/resource {:resource/ext-id "A"} :blacklist/user {:userid "user1" :name nil :email nil}}
             {:blacklist/resource {:resource/ext-id "B"} :blacklist/user {:userid "user1" :name nil :email nil}}
             {:blacklist/resource {:resource/ext-id "B"} :blacklist/user {:userid "user2" :name nil :email nil}}}
           (fetch {})))))
