(ns ^:integration rems.api.test-blacklist
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.api-key :as api-key]
            [rems.db.applications :as applications]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all])
  (:import [org.joda.time DateTimeUtils]))

(use-fixtures
  :once
  api-fixture
  (fn [f]
    ;; TODO this needs to be in the future so that we can use the
    ;; catalogue item we create. The DB time isn't overridden and
    ;; catalogue-item start defaults to now().
    (DateTimeUtils/setCurrentMillisFixed (.getTime #inst "2100-01-01"))
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
      handler))
(def ^:private assert-add-ok! (comp assert-response-is-ok add!))
(def ^:private assert-add-unprocessable-entity! (comp assert-response-is-unprocessable-entity
                                                      add!))

(defn- remove! [command]
  (-> (request :post "/api/blacklist/remove")
      (authenticate "42" +command-user+)
      (json-body command)
      handler))
(def ^:private assert-remove-ok! (comp assert-response-is-ok remove!))
(def ^:private assert-remove-unprocessable-entity! (comp assert-response-is-unprocessable-entity
                                                         remove!))

(deftest test-blacklist
  (api-key/add-api-key! "42")
  (test-helpers/create-user! {:userid +command-user+ :name "Owner" :email "owner@example.com"} :owner)
  (test-helpers/create-user! {:userid +fetch-user+} :reporter)
  (test-helpers/create-user! {:userid "user1" :mappings {"alt-id" "user1-alt-id"}})
  (test-helpers/create-user! {:userid "user2"})
  (test-helpers/create-user! {:userid "user3"})
  (let [_ (test-helpers/create-resource! {:resource-ext-id "A"})
        res-id-2 (test-helpers/create-resource! {:resource-ext-id "B"})
        _ (test-helpers/create-resource! {:resource-ext-id "C"})

        cat-id (test-helpers/create-catalogue-item! {:resource-id res-id-2})
        app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id]
                                                  :actor "user2"})
        get-app #(applications/get-application app-id)]
    (testing "initially no blacklist"
      (is (= [] (fetch {})))
      (is (= []
             (:application/blacklist (get-app)))))
    (testing "add three entries"
      (assert-add-ok! {:blacklist/user {:userid "user1"}
                       :blacklist/resource {:resource/ext-id "A"}
                       :comment "bad"})
      (assert-add-ok! {:blacklist/user {:userid "user1-alt-id"}
                       :blacklist/resource {:resource/ext-id "B"}
                       :comment "quite bad"})
      (assert-add-ok! {:blacklist/user {:userid "user2"}
                       :blacklist/resource {:resource/ext-id "B"}
                       :comment "very bad"})
      (is (= [{:blacklist/resource {:resource/ext-id "A"}
               :blacklist/user {:userid "user1" :name nil :email nil}
               :blacklist/added-by {:userid "owner" :name "Owner" :email "owner@example.com"}
               :blacklist/added-at "2100-01-01T00:00:00.000Z"
               :blacklist/comment "bad"}
              {:blacklist/resource {:resource/ext-id "B"}
               :blacklist/user {:userid "user1" :name nil :email nil}
               :blacklist/added-by {:userid "owner" :name "Owner" :email "owner@example.com"}
               :blacklist/added-at "2100-01-01T00:00:00.000Z"
               :blacklist/comment "quite bad"}
              {:blacklist/resource {:resource/ext-id "B"}
               :blacklist/user {:userid "user2" :name nil :email nil}
               :blacklist/added-by {:userid "owner" :name "Owner" :email "owner@example.com"}
               :blacklist/added-at "2100-01-01T00:00:00.000Z"
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
      (is (= []
             (simplify (fetch {:user "user1-alt-id-does-not-exist"})))
          "alternate identity of user1, no mapping exists")
      (is (= [{:resource/ext-id "A" :userid "user1"}
              {:resource/ext-id "B" :userid "user1"}]
             (simplify (fetch {:user "user1-alt-id"})))
          "alternate identity of user1, from a mapping")
      (is (= [{:resource/ext-id "B" :userid "user1"}
              {:resource/ext-id "B" :userid "user2"}]
             (simplify (fetch {:resource "B"}))))
      (is (= [{:resource/ext-id "B" :userid "user2"}]
             (simplify (fetch {:resource "B" :user "user2"})))))
    (testing "remove entry"
      (assert-remove-ok! {:blacklist/user {:userid "user2"}
                          :blacklist/resource {:resource/ext-id "B"}
                          :comment "oops"})
      (is (= []
             (fetch {:resource "B" :user "user2"}))))
    (testing "application is updated when user is removed from blacklist"
      (is (= []
             (:application/blacklist (get-app)))))
    (testing "add entry again"
      (assert-add-ok! {:blacklist/user {:userid "user2"}
                       :blacklist/resource {:resource/ext-id "B"}
                       :comment "again"})
      (is (= [{:resource/ext-id "B" :userid "user2"}]
             (simplify (fetch {:resource "B" :user "user2"})))))
    (testing "remove nonexistent entry"
      (assert-remove-ok! {:blacklist/user {:userid "user3"}
                          :blacklist/resource {:resource/ext-id "C"}
                          :comment "undo"})
      (is (= [{:resource/ext-id "A" :userid "user1"}
              {:resource/ext-id "B" :userid "user1"}
              {:resource/ext-id "B" :userid "user2"}]
             (simplify (fetch {})))))
    (testing "adding and removing non-existing user and resource should not change anything"
      (let [blacklist (simplify (fetch {}))]
        (is (= [{:resource/ext-id "A" :userid "user1"}
                {:resource/ext-id "B" :userid "user1"}
                {:resource/ext-id "B" :userid "user2"}] blacklist))
        (assert-add-unprocessable-entity! {:blacklist/user {:userid "definitely-not-found"}
                                           :blacklist/resource {:resource/ext-id "A"}
                                           :comment "not found"})
        (is (= blacklist (simplify (fetch {}))))
        (assert-add-unprocessable-entity! {:blacklist/user {:userid "user1"}
                                           :blacklist/resource {:resource/ext-id "definitely-not-found"}
                                           :comment "not found"})
        (is (= blacklist (simplify (fetch {}))))
        (assert-remove-unprocessable-entity! {:blacklist/user {:userid "definitely-not-found"}
                                              :blacklist/resource {:resource/ext-id "B"}
                                              :comment "not found"})
        (is (= blacklist (simplify (fetch {}))))
        (assert-remove-unprocessable-entity! {:blacklist/user {:userid "user2"}
                                              :blacklist/resource {:resource/ext-id "definitely-not-found"}
                                              :comment "not found"})
        (is (= blacklist (simplify (fetch {}))))))))
