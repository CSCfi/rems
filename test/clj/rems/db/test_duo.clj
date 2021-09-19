(ns ^:integration rems.db.test-duo
  (:require [clojure.test :refer :all]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.duo :as duo]
            [rems.db.test-data-helpers :as test-helpers]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-crud-duo
  (testing "empty start"
    (is (= [] (duo/get-duo-codes))))

  (testing "inserts with and without shorthand"
    (duo/upsert-duo-code! {:id "DUO:0000001"
                           :shorthand "TST"
                           :label {:en "test code"}
                           :description {:en "a db test code for DUO"}})
    (duo/upsert-duo-code! {:id "DUO:0000002"
                           :label {:en "shorthanded test code"}
                           :description {:en "a db test code for DUO without shorthand"}})
    (is (= [{:id "DUO:0000001"
             :shorthand "TST"
             :label {:en "test code"}
             :description {:en "a db test code for DUO"}}
            {:id "DUO:0000002"
             :label {:en "shorthanded test code"}
             :description {:en "a db test code for DUO without shorthand"}}]
           (duo/get-duo-codes))))

  (testing "upsert to update data"
    (duo/upsert-duo-code! {:id "DUO:0000001"
                           :shorthand "TST"
                           :label {:en "test code"
                                   :fi "testikoodi"}
                           :description {:en "a db test code for DUO"
                                         :fi "tietokantatestin DUO-koodi"}})
    (duo/upsert-duo-code! {:id "DUO:0000002"
                           :shorthand "SHT"
                           :label {:en "test code"}
                           :description {:en "a db test code for DUO now with shorthand"}})
    (is (= [{:id "DUO:0000001"
             :shorthand "TST"
             :label {:en "test code"
                     :fi "testikoodi"}
             :description {:en "a db test code for DUO"
                           :fi "tietokantatestin DUO-koodi"}}
            {:id "DUO:0000002"
             :shorthand "SHT",
             :label {:en "test code"}
             :description {:en "a db test code for DUO now with shorthand"}}]
           (duo/get-duo-codes)))))
