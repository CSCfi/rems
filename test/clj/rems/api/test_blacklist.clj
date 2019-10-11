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
    (add! {:user "user1"
           :resource "A"
           :comment "bad"})
    (add! {:user "user1"
           :resource "B"
           :comment "quite bad"})
    (add! {:user "user2"
           :resource "B"
           :comment "very bad"})
    (is (= #{{:resource "A" :user {:userid "user1" :name nil :email nil}}
             {:resource "B" :user {:userid "user1" :name nil :email nil}}
             {:resource "B" :user {:userid "user2" :name nil :email nil}}}
           (fetch {}))))
  (testing "query parameters"
    (is (= #{{:resource "A" :user {:userid "user1" :name nil :email nil}}
             {:resource "B" :user {:userid "user1" :name nil :email nil}}}
           (fetch {:user "user1"})))
    (is (= #{{:resource "B" :user {:userid "user1" :name nil :email nil}}
             {:resource "B" :user {:userid "user2" :name nil :email nil}}}
           (fetch {:resource "B"})))
    (is (= #{{:resource "B" :user {:userid "user2" :name nil :email nil}}}
           (fetch {:resource "B" :user "user2"}))))
  (testing "remove entry"
    (remove! {:user "user2"
              :resource "B"
              :comment "oops"})
    (is (= #{}
           (fetch {:resource "B" :user "user2"}))))
  (testing "add entry again"
    (add! {:user "user2"
           :resource "B"
           :comment "again"})
    (is (= #{{:resource "B" :user {:userid "user2" :name nil :email nil}}}
           (fetch {:resource "B" :user "user2"}))))
  (testing "remove nonexistent entry"
    (remove! {:user "user3"
              :resource "C"
              :comment "undo"})
    (is (= #{{:resource "A" :user {:userid "user1" :name nil :email nil}}
             {:resource "B" :user {:userid "user1" :name nil :email nil}}
             {:resource "B" :user {:userid "user2" :name nil :email nil}}}
           (fetch {})))))
