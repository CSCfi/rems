(ns rems.test.db
  "Namespace for tests that use an actual database."
  (:require [rems.db.core :as db]
            [rems.contents :as contents]
            [rems.form :as form]
            [rems.env :refer [*db*]]
            [luminus-migrations.core :as migrations]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [rems.config :refer [env]]
            [mount.core :as mount]
            [conman.core :as conman]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'rems.config/env
      #'rems.env/*db*)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (f)
    (mount/stop)))

(use-fixtures :each
  (fn [f]
    (conman/with-transaction [rems.env/*db*]
      (jdbc/db-set-rollback-only! rems.env/*db*)
      (f))))

(deftest ^:integration test-get-catalogue-items
  (testing "without catalogue items"
    (is (empty? (db/get-catalogue-items))))

  (testing "with test database"
    (db/create-test-data!)
    (is (= ["B" "ELFA Corpus"] (sort (map :title (db/get-catalogue-items)))) "should find two items")

    (let [item-from-list (second (db/get-catalogue-items))
          item-by-id (db/get-catalogue-item {:id (:id item-from-list)})]
      (is (= (select-keys item-from-list [:id :title])
             (select-keys item-by-id [:id :title])) "should find catalogue item by id"))))

(deftest ^:integration test-form
  (db/create-test-data!)
  (let [elfa (first (filter #(= (:title %) "ELFA Corpus") (db/get-catalogue-items)))
        form-fi (form/get-form-for (:id elfa) "fi")
        form-en (form/get-form-for (:id elfa) "en")]
    (is elfa "sanity check")
    (is (= "entitle" (:title form-en)) "title")
    (is (= ["A" "B" "C"] (map :title (:items form-en))) "items should be in order")
    (is (= "fititle" (:title form-fi)) "title")
    (is (= ["A"] (map :title (:items form-fi))) "there should be only one item")))
