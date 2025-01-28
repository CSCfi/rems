(ns ^:integration rems.db.test-user-mappings
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.cache :as cache]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.user-mappings]
            [rems.db.test-data-helpers :as test-helpers]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-user-mapping-caches
  (testing "cache reload works"
    (let [user-1 (test-helpers/create-user! {:userid "alice"})
          user-2 (test-helpers/create-user! {:userid "bob"})
          _ (rems.db.user-mappings/create-user-mapping! {:userid user-1
                                                         :ext-id-attribute "elixirId"
                                                         :ext-id-value "elixir-alice"})
          _ (rems.db.user-mappings/create-user-mapping! {:userid user-2
                                                         :ext-id-attribute "elixirId"
                                                         :ext-id-value "elixir-bob"})
          _ (rems.db.user-mappings/create-user-mapping! {:userid user-2
                                                         :ext-id-attribute "altId"
                                                         :ext-id-value "bob-alt"})]
      ;; force cache reload
      (cache/set-uninitialized! rems.db.user-mappings/user-mappings-cache)

      ;; verify user-mappings-cache is cached correctly
      (is (= {"alice" [{:userid "alice"
                        :ext-id-attribute "elixirId"
                        :ext-id-value "elixir-alice"}]
              "bob" [{:userid "bob"
                      :ext-id-attribute "elixirId"
                      :ext-id-value "elixir-bob"}
                     {:userid "bob"
                      :ext-id-attribute "altId"
                      :ext-id-value "bob-alt"}]}
             (into {} (cache/entries! rems.db.user-mappings/user-mappings-cache))))

      (testing "dependent caches"
        ;; verify by-extidattribute is cached correctly (depends on user-mappings-cache)
        (is (= {"elixirId" [{:userid "alice"
                             :ext-id-attribute "elixirId"
                             :ext-id-value "elixir-alice"}
                            {:userid "bob"
                             :ext-id-attribute "elixirId"
                             :ext-id-value "elixir-bob"}]
                "altId" [{:userid "bob"
                          :ext-id-attribute "altId"
                          :ext-id-value "bob-alt"}]}
               (into {} (cache/entries! @#'rems.db.user-mappings/by-extidattribute))))

      ;; verify by-extidvalue is cached correctly (depends on user-mappings-cache)
        (is (= {"elixir-alice" [{:userid "alice"
                                 :ext-id-attribute "elixirId"
                                 :ext-id-value "elixir-alice"}]
                "elixir-bob" [{:userid "bob"
                               :ext-id-attribute "elixirId"
                               :ext-id-value "elixir-bob"}]
                "bob-alt" [{:userid "bob"
                            :ext-id-attribute "altId"
                            :ext-id-value "bob-alt"}]}
               (into {} (cache/entries! @#'rems.db.user-mappings/by-extidvalue))))))))