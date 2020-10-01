(ns ^:integration rems.api.test-catalogue-items
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.applications :as applications]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :each
  api-fixture)

(deftest catalogue-items-api-test
  (let [api-key "42"
        user-id "alice"
        form-id (test-helpers/create-form! {:form/title "form name" :organization {:organization/id "organization1"}})
        ;; can create catalogue items with mixed organizations:
        wf-id (test-helpers/create-workflow! {:title "workflow name" :organization {:organization/id "abc"}})
        res-id (test-helpers/create-resource! {:resource-ext-id "resource ext id" :organization {:organization/id "organization1"}})]
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
                                                  :organization {:organization/id organization}
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
                      :organization {:organization/id "organization1" :organization/name {:fi "Organization 1" :en "Organization 1" :sv "Organization 1"} :organization/short-name {:fi "ORG 1" :en "ORG 1" :sv "ORG 1"}}
                      :localizations {}}
                     (select-keys data [:id :organization :workflow-name :form-name :resource-name :localizations])))))
          (testing "and fetch non-existing item"
            (let [response (-> (request :get "/api/catalogue-items/777777777")
                               (authenticate api-key user-id)
                               handler)]
              (is (response-is-not-found? response))
              (is (= "application/json" (get-in response [:headers "Content-Type"])))))))

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
        form-id (test-helpers/create-form! {:organization {:organization/id "organization1"}})
        wf-id (test-helpers/create-workflow! {:organization {:organization/id "organization1"}})
        res-id (test-helpers/create-resource! {:organization {:organization/id "organization1"}})]
    (testing "create"
      (let [create (-> (request :post "/api/catalogue-items/create")
                       (authenticate api-key owner)
                       (json-body {:form form-id
                                   :resid res-id
                                   :wfid wf-id
                                   :organization {:organization/id "organization1"}
                                   :localizations {:en {:title "En title"}
                                                   :sv {:title "Sv title"
                                                        :infourl "http://info.se"}}})
                       handler
                       read-ok-body)
            id (:id create)]
        (is (:success create))
        (let [app-id (test-helpers/create-application! {:catalogue-item-ids [id]
                                                        :actor "alice"})
              get-app #(applications/get-application app-id)]
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
          (testing "... and edit (as owner)"
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
                  (is (= id (:id data)))
                  (is (= {:title "En title"
                          :infourl nil}
                         (dissoc (get-in data [:localizations :en]) :id :langcode)))
                  (is (= {:title "Sv title 2"
                          :infourl nil}
                         (dissoc (get-in data [:localizations :sv]) :id :langcode)))
                  (is (= {:title "Fi title"
                          :infourl "http://info.fi"}
                         (dissoc (get-in data [:localizations :fi]) :id :langcode)))))))
          (testing "... and edit (as organization owner)"
            (let [response (-> (request :put "/api/catalogue-items/edit")
                               (authenticate api-key "organization-owner1")
                               (json-body {:id id
                                           :localizations {:sv {:title "Sv title 2"
                                                                :infourl nil}
                                                           :fi {:title "Fi title 2"
                                                                :infourl "http://info.fi"}}})
                               handler
                               read-ok-body)]
              (is (:success response) (pr-str response))))
          (testing "... and edit (as organization owner for another organization)"
            (let [response (-> (request :put "/api/catalogue-items/edit")
                               (authenticate api-key "organization-owner2")
                               (json-body {:id id
                                           :localizations {:sv {:title "Sv title 2"
                                                                :infourl nil}
                                                           :fi {:title "Fi title 2"
                                                                :infourl "http://info.fi"}}})
                               handler)]
              (is (response-is-forbidden? response))
              (is (= "no access to organization \"organization1\"" (read-body response))))))))

    (testing "edit nonexisting"
      (let [response (-> (request :put "/api/catalogue-items/edit")
                         (authenticate api-key owner)
                         (json-body {:id 999999999
                                     :localizations {:sv {:title "Sv title 2"
                                                          :infourl nil}
                                                     :fi {:title "Fi title"
                                                          :infourl "http://info.fi"}}})
                         handler)]
        (is (response-is-not-found? response))))))


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
        resource-id (test-helpers/create-resource! {:organization {:organization/id "organization1"}})
        old-form-id (test-helpers/create-form! {:form/title "old form"
                                                :organization {:organization/id "organization1"}})
        new-form-id (test-helpers/create-form! {:form/title "new form"
                                                :organization {:organization/id "organization1"}})
        old-catalogue-item-id (test-helpers/create-catalogue-item!
                               {:organization {:organization/id "organization1"}
                                :title {:en "change-form-test catalogue item en"
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
          (is (not (:enabled old-catalogue-item))))

        (let [same-keys [:wfid :workflow-name :resid :resource-id :resource-name]]
          (is (= (select-keys old-catalogue-item same-keys)
                 (select-keys new-catalogue-item same-keys))))

        (doseq [langcode (into (keys (:localizations old-catalogue-item))
                               (keys (:localizations new-catalogue-item)))]
          (is (= (dissoc (get-in old-catalogue-item [:localizations langcode]) :id)
                 (dissoc (get-in new-catalogue-item [:localizations langcode]) :id))))))
    (testing "can change to form that's in another organization"
      (let [form-id (test-helpers/create-form! {:form/title "wrong organization"
                                                :organization {:organization/id "organization2"}})
            response (-> (request :post (str "/api/catalogue-items/" old-catalogue-item-id "/change-form"))
                         (authenticate api-key "owner")
                         (json-body {:form form-id})
                         handler
                         read-ok-body)]
        (is (true? (:success response)))))
    (testing "can change form as organization owner"
      (is (true? (-> (request :post (str "/api/catalogue-items/" old-catalogue-item-id "/change-form"))
                     (authenticate api-key "organization-owner1")
                     (json-body {:form new-form-id})
                     handler
                     read-ok-body
                     :success))))
    (testing "can't change form as owner of different organization"
      (let [response (-> (request :post (str "/api/catalogue-items/" old-catalogue-item-id "/change-form"))
                         (authenticate api-key "organization-owner2")
                         (json-body {:form new-form-id})
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "no access to organization \"organization1\"" (read-body response)))))))

(deftest test-enable-archive
  (let [api-key "42"
        res-id (test-helpers/create-resource! {:resource-ext-id "resource ext id" :organization {:organization/id "organization1"}})
        id (test-helpers/create-catalogue-item!
            {:organization {:organization/id "organization1"}
             :title {:en "en"
                     :fi "fi"}
             :resource-id res-id})
        fetch #(api-call :get (str "/api/catalogue-items/" id)
                         nil
                         api-key "owner")]
    (doseq [user ["owner" "organization-owner1"]]
      (testing user
        (testing "disable"
          (is (:success (api-call :put "/api/catalogue-items/enabled"
                                  {:id id
                                   :enabled false}
                                  api-key user)))
          (is (false? (:enabled (fetch)))))
        (testing "enable"
          (is (:success (api-call :put "/api/catalogue-items/enabled"
                                  {:id id
                                   :enabled true}
                                  api-key user)))
          (is (true? (:enabled (fetch)))))
        (testing "archive"
          (is (:success (api-call :put "/api/catalogue-items/archived"
                                  {:id id
                                   :archived true}
                                  api-key user)))
          (is (true? (:archived (fetch)))))
        (testing "unarchive"
          (is (:success (api-call :put "/api/catalogue-items/archived"
                                  {:id id
                                   :archived false}
                                  api-key user)))
          (is (false? (:archived (fetch)))))))
    (testing "incorrect organization owner can't"
      (testing "enable"
        (let [response (-> (request :put "/api/catalogue-items/enabled")
                           (authenticate api-key "organization-owner2")
                           (json-body {:id id :enabled true})
                           handler)]
          (is (response-is-forbidden? response))))
      (testing "archive"
        (let [response (-> (request :put "/api/catalogue-items/archived")
                           (authenticate api-key "organization-owner2")
                           (json-body {:id id :archived true})
                           handler)]
          (is (response-is-forbidden? response)))))))
