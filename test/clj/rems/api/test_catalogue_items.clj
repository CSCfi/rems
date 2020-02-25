(ns ^:integration rems.api.test-catalogue-items
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.applications :as applications]
            [rems.db.test-data :as test-data]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest catalogue-items-api-test
  (let [api-key "42"
        user-id "alice"
        form-id (test-data/create-form! {:form/title "form name" :form/organization "organization1"})
        wf-id (test-data/create-workflow! {:title "workflow name" :organization "organization1"})
        res-id (test-data/create-resource! {:resource-ext-id "resource ext id" :organization "organization1"})]
    (let [data (-> (request :get "/api/catalogue-items/")
                   (authenticate api-key user-id)
                   handler
                   read-body)
          item (first data)]
      (is (not (str/blank? (:resid item)))))
    (let [create-catalogue-item (fn [user-id organization]
                                  (-> (request :post "/api/catalogue-items/create")
                                      (authenticate api-key user-id)
                                      (json-body {:form form-id
                                                  :resid res-id
                                                  :wfid wf-id
                                                  :organization organization
                                                  :archived true
                                                  :localizations {}})
                                      handler
                                      read-body))]
        (testing "create as owner"
          (let [data (create-catalogue-item "owner" "organization1")
                id (:id data)]
            (is (:success data))
            (is (number? id))
            (testing "and fetch"
              (let [data (-> (request :get (str "/api/catalogue-items/" id))
                             (authenticate api-key user-id)
                             handler
                             read-body)]
                (is (= {:id id
                        :workflow-name "workflow name"
                        :form-name "form name"
                        :resource-name "resource ext id"
                        :organization "organization1"
                        :localizations {}}
                       (select-keys data [:id :organization :workflow-name :form-name :resource-name :localizations])))))
            (testing "and fetch non-existing item"
              (let [response (-> (request :get "/api/catalogue-items/777777777")
                                 (authenticate api-key user-id)
                                 handler)]
                (is (response-is-not-found? response))
                (is (= "application/json" (get-in response [:headers "Content-Type"])))))))

        (testing "create with mismatched organization"
          (let [data (create-catalogue-item "owner" "nbn")]
            (is (not (:success data)))
            (is (= [{:type "t.administration.errors/organization-mismatch"
                     :form {:id form-id :organization "organization1"}
                     :resource {:id res-id :organization "organization1"}
                     :workflow {:id wf-id :organization "organization1"}}]
                   (:errors data)))))

        (testing "create as organization owner"
          (testing "with correct organization"
            (let [data (create-catalogue-item "organization-owner1" "organization1")
                  id (:id data)]
              (is (:success data))
              (is (number? id))))

          (testing "with incorrect organization"
            (let [data (create-catalogue-item "organization-owner2" "organization1")]
              (is (not (:success data)))))))))

(deftest catalogue-items-edit-test
  (let [api-key "42"
        owner "owner"
        user "alice"
        form-id (test-data/create-form! {})
        wf-id (test-data/create-workflow! {})
        res-id (test-data/create-resource! {})]
    (testing "create"
      (let [create (-> (request :post "/api/catalogue-items/create")
                       (authenticate api-key owner)
                       (json-body {:form form-id
                                   :resid res-id
                                   :wfid wf-id
                                   :organization "default"
                                   :localizations {:en {:title "En title"}
                                                   :sv {:title "Sv title"
                                                        :infourl "http://info.se"}}})
                       handler
                       read-ok-body)
            id (:id create)]
        (is (:success create))
        (let [app-id (test-data/create-application! {:catalogue-item-ids [id]
                                                     :actor "alice"})
              get-app #(applications/get-unrestricted-application app-id)]
          (is (= {:sv "http://info.se"}
                 (:catalogue-item/infourl
                  (first (:application/resources (get-app))))))
          (testing "... and fetch"
            (let [data (-> (request :get (str "/api/catalogue-items/" id))
                           (authenticate api-key user)
                           handler
                           read-ok-body)]
              (is (= id (:id data)))
              (is (= {:title "En title"
                      :infourl nil}
                     (dissoc (get-in data [:localizations :en]) :id :langcode)))
              (is (= {:title "Sv title"
                      :infourl "http://info.se"}
                     (dissoc (get-in data [:localizations :sv]) :id :langcode)))))
          (testing "... and edit"
            (let [response (-> (request :put "/api/catalogue-items/edit")
                               (authenticate api-key owner)
                               (json-body {:id id
                                           :localizations {:sv {:title "Sv title 2"
                                                                :infourl nil}
                                                           :fi {:title "Fi title"
                                                                :infourl "http://info.fi"}}})
                               handler
                               read-ok-body)]
              (is (:success response) (pr-str response))
              (testing "application is updated when catalogue item is edited"
                (is (= {:fi "http://info.fi"}
                       (:catalogue-item/infourl
                        (first (:application/resources (get-app)))))))
              (testing "... and fetch"
                (let [data (-> (request :get (str "/api/catalogue-items/" id))
                               (authenticate api-key user)
                               handler
                               read-ok-body)]
                  (prn data)
                  (is (= id (:id data)))
                  (is (= {:title "En title"
                          :infourl nil}
                         (dissoc (get-in data [:localizations :en]) :id :langcode)))
                  (is (= {:title "Sv title 2"
                          :infourl nil}
                         (dissoc (get-in data [:localizations :sv]) :id :langcode)))
                  (is (= {:title "Fi title"
                          :infourl "http://info.fi"}
                         (dissoc (get-in data [:localizations :fi]) :id :langcode))))))))))))


(deftest catalogue-items-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/catalogue-items"))
                       handler)
          body (read-body response)]
      (is (response-is-unauthorized? response))
      (is (= "unauthorized" body))))
  (testing "item without authentication"
    (let [response (-> (request :get (str "/api/catalogue-items/2"))
                       handler)
          body (read-body response)]
      (is (response-is-unauthorized? response))
      (is (= "unauthorized" body))))
  (testing "create without authentication"
    (let [response (-> (request :post (str "/api/catalogue-items/create"))
                       handler)
          body (read-body response)]
      (is (response-is-unauthorized? response))
      (is (str/includes? body "Invalid anti-forgery token"))))
  (testing "create with wrong API-Key"
    (is (= "Invalid anti-forgery token"
           (-> (request :post (str "/api/catalogue-items/create"))
               (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
               (json-body {:form 1
                           :resid 1
                           :wfid 1
                           :localizations {}})
               handler
               (read-body)))))
  (testing "edit without authentication"
    (let [response (-> (request :post (str "/api/catalogue-items/edit"))
                       (json-body {:id 1
                                   :localizations {:en {:title "malicious localization"}}})
                       handler)
          body (read-body response)]
      (is (response-is-unauthorized? response))
      (is (str/includes? body "Invalid anti-forgery token"))))
  (testing "edit with wrong API-Key"
    (is (= "Invalid anti-forgery token"
           (-> (request :put (str "/api/catalogue-items/edit"))
               (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
               (json-body {:id 1
                           :localizations {:en {:title "malicious localization"}}})
               handler
               (read-body))))))

(deftest change-form-test
  (let [api-key "42"
        resource-id (test-data/create-resource! {})
        old-form-id (test-data/create-form! {:form/title "old form"})
        new-form-id (test-data/create-form! {:form/title "new form"})
        old-catalogue-item-id (test-data/create-catalogue-item!
                               {:title {:en "change-form-test catalogue item en"
                                        :fi "change-form-test catalogue item fi"}
                                :resource-id resource-id
                                :form-id old-form-id})]
    (testing "when the form is changed a new catalogue item is created"
      (let [new-catalogue-item-id (-> (request :post (str "/api/catalogue-items/" old-catalogue-item-id "/change-form"))
                                      (authenticate api-key "owner")
                                      (json-body {:form new-form-id})
                                      handler
                                      read-ok-body
                                      :catalogue-item-id)
            new-catalogue-item (-> (request :get (str "/api/catalogue-items/" new-catalogue-item-id))
                                   (authenticate api-key "owner")
                                   handler
                                   read-ok-body)
            old-catalogue-item (-> (request :get (str "/api/catalogue-items/" old-catalogue-item-id))
                                   (authenticate api-key "owner")
                                   handler
                                   read-ok-body)]
        (testing "the new item"
          (is (:enabled new-catalogue-item))
          (is (= new-form-id (:formid new-catalogue-item)) "has the new changed form id"))

        (testing "the old item"
          (is (:archived old-catalogue-item))
          (is (:expired old-catalogue-item))
          (is (not (:enabled old-catalogue-item))))

        (let [same-keys [:wfid :workflow-name :resid :resource-id :resource-name]]
          (is (= (select-keys old-catalogue-item same-keys)
                 (select-keys new-catalogue-item same-keys))))

        (doseq [langcode (into (keys (:localizations old-catalogue-item))
                               (keys (:localizations new-catalogue-item)))]
          (is (= (dissoc (get-in old-catalogue-item [:localizations langcode]) :id)
                 (dissoc (get-in new-catalogue-item [:localizations langcode]) :id))))

        (is (= (:end old-catalogue-item) (:start new-catalogue-item)))))))
