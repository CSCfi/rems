(ns rems.test.db
  "Namespace for tests that use an actual database."
  (:require [rems.db.core :as db]
            [rems.contents :as contents]
            [rems.form :as form]
            [rems.db.applications :as applications]
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
    (db/create-resource! {:id 1 :resid "http://urn.fi/urn:nbn:fi:lb-201403262" :prefix "nbn" :modifieruserid 1})
    (db/create-catalogue-item! {:title "ELFA Corpus" :form nil :resid 1})
    (db/create-catalogue-item! {:title "B" :form nil :resid nil})
    (is (= ["B" "ELFA Corpus"] (sort (map :title (db/get-catalogue-items))))
        "should find two items")
    (let [item-from-list (second (db/get-catalogue-items))
          item-by-id (db/get-catalogue-item {:id (:id item-from-list)})]
      (is (= (select-keys item-from-list [:id :title])
             (select-keys item-by-id [:id :title]))
          "should find catalogue item by id"))))

(deftest ^:integration test-form
  (let [meta (db/create-form-meta! {:title "metatitle" :user 0})
        item (db/create-catalogue-item! {:title "item" :form (:id meta) :resid nil})
        form-en (db/create-form! {:title "entitle" :user 0})
        form-fi (db/create-form! {:title "fititle" :user 0})
        item-c (db/create-form-item!
                {:title "C" :type "text" :inputprompt "prompt" :user 0 :value 0})
        item-a (db/create-form-item!
                {:title "A" :type "text" :inputprompt "prompt" :user 0 :value 0})
        item-b (db/create-form-item!
                {:title "B" :type "text" :inputprompt "prompt" :user 0 :value 0})]
    (db/link-form-meta! {:meta (:id meta) :form (:id form-en) :lang "en" :user 0})
    (db/link-form-meta! {:meta (:id meta) :form (:id form-fi) :lang "fi" :user 0})
    (db/link-form-item! {:form (:id form-en) :itemorder 2 :item (:id item-b) :user 0})
    (db/link-form-item! {:form (:id form-en) :itemorder 1 :item (:id item-a) :user 0})
    (db/link-form-item! {:form (:id form-en) :itemorder 3 :item (:id item-c) :user 0})
    (db/link-form-item! {:form (:id form-fi) :itemorder 1 :item (:id item-a) :user 0})

    (is (:id item) "sanity check")

    (testing "get form for catalogue item"
      (let [form-fi (form/get-form-for (:id item) "fi")
            form-en (form/get-form-for (:id item) "en")]
        (is (= "entitle" (:title form-en)) "title")
        (is (= ["A" "B" "C"] (map :title (:items form-en))) "items should be in order")
        (is (= "fititle" (:title form-fi)) "title")
        (is (= ["A"] (map :title (:items form-fi))) "there should be only one item")))

    (testing "get partially filled form"
      (let [app (db/create-application! {:item (:id item) :user 0})]
        (is app "sanity check")
        (db/save-field-value! {:application (:id app)
                               :form (:id form-en)
                               :item (:id item-b)
                               :user 0
                               :value "B"})
        (let [f (form/get-form-for (:id item) "en" (:id app))]
          (is (= [nil "B" nil] (map :value (:items f)))))

        (testing "reset field value"
          (db/clear-field-value! {:application (:id app)
                                  :form (:id form-en)
                                  :item (:id item-b)})
          (db/save-field-value! {:application (:id app)
                                 :form (:id form-en)
                                 :item (:id item-b)
                                 :user 0
                                 :value "X"})
          (let [f (form/get-form-for (:id item) "en" (:id app))]
            (is (= [nil "X" nil] (map :value (:items f))))))))))

(deftest ^:integration test-applications
  (let [item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil}))
        app (#'form/create-new-draft item)]
    (is (= [{:id app :state "draft" :catid item}]
           (map #(select-keys % [:id :state :catid])
                (applications/get-applications))))
    (db/update-application-state! {:id app :user 0 :state "approved"})
    (is (= [{:id app :state "approved" :catid item}]
           (map #(select-keys % [:id :state :catid])
                (applications/get-applications))))))
