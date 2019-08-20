(ns ^:integration rems.api.test-workflows
  (:require [clojure.test :refer :all]
            [rems.api.services.licenses :as licenses]
            [rems.api.services.workflow :as workflow]
            [rems.api.testing :refer :all]
            [rems.common-util :refer [index-by]]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [sync-with-database-time]]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest workflows-api-test
  (testing "list"
    (let [data (-> (request :get "/api/workflows")
                   (authenticate "42" "owner")
                   handler
                   assert-response-is-ok
                   read-body)]
      (is (coll-is-not-empty? data))))

  ;; TODO: create a new auto-approve workflow in the style of dynamic workflows
  #_(testing "create auto-approved workflow"
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
                    :actors []}
                   (select-keys workflow [:id :organization :title :actors])))))))

  (testing "create dynamic workflow"
    (let [body (-> (request :post "/api/workflows/create")
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
      (sync-with-database-time)
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
                             :handlers [{:userid "bob" :email "bob@example.com" :name "Bob Approver"}
                                        {:userid "carl" :email "carl@example.com" :name "Carl Reviewer"}]}
                  :enabled true
                  :archived false}
                 (select-keys workflow [:id :organization :title :workflow :enabled :archived]))))))))

(deftest workflows-update-test
  (let [api-key "42"
        user-id "owner"
        wfid (test-data/create-dynamic-workflow! {:organization "abc"
                                                  :title "dynamic workflow"
                                                  :handlers ["bob" "carl"]})
        lic-id (test-data/create-license! {})
        _ (db/create-workflow-license! {:wfid wfid :licid lic-id})

        archive-license! #(licenses/update-license! {:id lic-id
                                                     :enabled true
                                                     :archived %})

        ;; this is a subset of what we expect to get from the api
        expected {:id wfid
                  :organization "abc"
                  :title "dynamic workflow"
                  :workflow {:type "workflow/dynamic"
                             :handlers [{:userid "bob" :email "bob@example.com" :name "Bob Approver"}
                                        {:userid "carl" :email "carl@example.com" :name "Carl Reviewer"}]}
                  :enabled true
                  :expired false
                  :archived false}
        fetch (fn []
                (let [wfs (-> (request :get "/api/workflows" {:archived true :expired true :disabled true})
                              (authenticate api-key user-id)
                              handler
                              read-ok-body)]
                  (select-keys
                   (first (filter #(= wfid (:id %)) wfs))
                   (keys expected))))
        update! #(-> (request :put "/api/workflows/update")
                     (json-body (merge {:id wfid} %))
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)]
    (sync-with-database-time)
    (testing "before changes"
      (is (= expected (fetch))))
    (testing "disable and archive"
      (is (:success (update! {:enabled false :archived true})))
      (is (= (assoc expected
                    :enabled false
                    :archived true)
             (fetch))))
    (testing "re-enable"
      (is (:success (update! {:enabled true})))
      (is (= (assoc expected
                    :archived true)
             (fetch))))
    (testing "unarchive"
      (is (:success (update! {:archived false})))
      (is (= expected
             (fetch))))
    (testing "change title"
      (is (:success (update! {:title "x"})))
      (is (= (assoc expected
                    :title "x")
             (fetch))))
    (testing "change handlers"
      (is (:success (update! {:handlers ["owner" "alice"]})))
      (is (= (assoc expected
                    :title "x"
                    :workflow {:type "workflow/dynamic"
                               :handlers [{:email "owner@example.com" :name "Owner" :userid "owner"}
                                          {:email "alice@example.com" :name "Alice Applicant" :userid "alice"}]})
             (fetch))))
    (testing "cannot unarchive if license is archived"
      (update! {:archived true})
      (archive-license! true)
      (is (not (:success (update! {:archived false}))))
      (archive-license! false)
      (is (:success (update! {:archived false}))))))

(deftest workflows-api-filtering-test
  (let [enabled-wf (test-data/create-dynamic-workflow! {})
        disabled-wf (test-data/create-dynamic-workflow! {})
        _ (workflow/update-workflow! {:id disabled-wf
                                      :enabled false})
        enabled-and-disabled-wfs (set (map :id (-> (request :get "/api/workflows" {:disabled true})
                                                   (authenticate "42" "owner")
                                                   handler
                                                   assert-response-is-ok
                                                   read-body)))
        enabled-wfs (set (map :id (-> (request :get "/api/workflows")
                                      (authenticate "42" "owner")
                                      handler
                                      assert-response-is-ok
                                      read-body)))]
    (is (contains? enabled-and-disabled-wfs enabled-wf))
    (is (contains? enabled-and-disabled-wfs disabled-wf))
    (is (contains? enabled-wfs enabled-wf))
    (is (not (contains? enabled-wfs disabled-wf)))))

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
