(ns ^:integration rems.test.db
  "Namespace for tests that use an actual database."
  (:require [rems.db.core :as db]
            [rems.context :as context]
            [rems.contents :as contents]
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

(deftest test-get-catalogue-items
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

(deftest test-form
  (binding [context/*user* "test-user"]
    (let [meta (db/create-form-meta! {:title "metatitle" :user context/*user*})
          item (db/create-catalogue-item! {:title "item" :form (:id meta) :resid nil})
          form-en (db/create-form! {:title "entitle" :user context/*user*})
          form-fi (db/create-form! {:title "fititle" :user context/*user*})
          item-c (db/create-form-item!
                  {:title "C" :type "text" :inputprompt "prompt" :user context/*user* :value 0})
          item-a (db/create-form-item!
                  {:title "A" :type "text" :inputprompt "prompt" :user context/*user* :value 0})
          item-b (db/create-form-item!
                  {:title "B" :type "text" :inputprompt "prompt" :user context/*user* :value 0})]
      (db/link-form-meta! {:meta (:id meta) :form (:id form-en) :lang "en" :user context/*user*})
      (db/link-form-meta! {:meta (:id meta) :form (:id form-fi) :lang "fi" :user context/*user*})
      (db/link-form-item! {:form (:id form-en) :itemorder 2 :item (:id item-b) :user context/*user*})
      (db/link-form-item! {:form (:id form-en) :itemorder 1 :item (:id item-a) :user context/*user*})
      (db/link-form-item! {:form (:id form-en) :itemorder 3 :item (:id item-c) :user context/*user*})
      (db/link-form-item! {:form (:id form-fi) :itemorder 1 :item (:id item-a) :user context/*user*})

      (is (:id item) "sanity check")

      (testing "get form for catalogue item"
        (let [form-fi (binding [context/*lang* :fi]
                        (applications/get-form-for (:id item)))
              form-en (binding [context/*lang* :en]
                        (applications/get-form-for (:id item)))]
          (is (= "entitle" (:title form-en)) "title")
          (is (= ["A" "B" "C"] (map :title (:items form-en))) "items should be in order")
          (is (= "fititle" (:title form-fi)) "title")
          (is (= ["A"] (map :title (:items form-fi))) "there should be only one item")))

      (testing "get partially filled form"
        (binding [context/*lang* :en]
          (let [app-id (applications/create-new-draft (:id item))]
            (is app-id "sanity check")
            (db/save-field-value! {:application app-id
                                   :form (:id form-en)
                                   :item (:id item-b)
                                   :user context/*user*
                                   :value "B"})
            (let [f (applications/get-form-for (:id item) app-id)]
              (is (= app-id (:application f)))
              (is (= "draft" (:state f)))
              (is (= [nil "B" nil] (map :value (:items f)))))

            (testing "reset field value"
              (db/clear-field-value! {:application app-id
                                      :form (:id form-en)
                                      :item (:id item-b)})
              (db/save-field-value! {:application app-id
                                     :form (:id form-en)
                                     :item (:id item-b)
                                     :user context/*user*
                                     :value "X"})
              (let [f (applications/get-form-for (:id item) app-id)]
                (is (= [nil "X" nil] (map :value (:items f))))))))))))

(deftest test-applications
  (binding [context/*user* "test-user"]
    (let [item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil}))
          app (applications/create-new-draft item)]
      (is (= app (applications/get-draft-id-for item)))
      (is (= [{:id app :state "draft" :catid item}]
             (map #(select-keys % [:id :state :catid])
                  (applications/get-applications))))
      (db/update-application-state! {:id app :user context/*user* :state "approved"})
      (is (nil? (applications/get-draft-id-for item)))
      (is (= [{:id app :state "approved" :catid item}]
             (map #(select-keys % [:id :state :catid])
                  (applications/get-applications)))))))
