(ns ^:integration rems.api.test-organizations
  (:require [clojure.test :refer :all]
            [medley.core :refer [find-first]]
            [rems.api.testing :refer :all]
            [rems.db.api-key :as api-key]
            [rems.db.test-data-helpers :as test-helpers]
            [ring.mock.request :refer :all]))

(use-fixtures :each api-fixture)

(def organization-id "organizations-api-test-org")
(def organization-id-2 "organizations-api-test-org-2")

(deftest organizations-api-test
  (let [api-key "42"
        user "alice"
        owner "owner"
        org-owner1 "organization-owner1"
        org-owner2 "organization-owner2"
        get-org (fn [userid id] (api-call :get (str "/api/organizations/" id)
                                          nil
                                          api-key userid))
        get-orgs (fn [userid] (api-call :get (str "/api/organizations")
                                        nil
                                        api-key userid))]
    (api-key/add-api-key! api-key)
    (test-helpers/create-user! {:userid user})
    (test-helpers/create-user! {:userid owner :name "Owner" :email "owner@example.com"} :owner)
    (test-helpers/create-user! {:userid org-owner1 :name "Organization Owner 1" :email "organization-owner1@example.com"})
    (test-helpers/create-user! {:userid org-owner2 :name "Organization Owner 2" :email "organization-owner2@example.com"})

    (testing "fetch nonexistent"
      (let [resp (api-response :get "/api/organizations/9999999999999999"
                               nil
                               api-key owner)]
        (is (response-is-not-found? resp))
        (is (= {:error "not found"} (read-body resp)))))

    (testing "create organization"
      (let [data (api-call :post "/api/organizations/create"
                           {:organization/id organization-id
                            :organization/name {:fi "Organisaatiot API Test ORG"
                                                :en "Organizations API Test ORG"}
                            :organization/short-name {:fi "ORG" :en "ORG"}
                            :organization/owners [{:userid org-owner1}]
                            :organization/review-emails [{:email "test@organization.test.org"
                                                          :name {:fi "Organisaatiot API Test ORG Katselmoijat"
                                                                 :en "Organizations API Test ORG Reviewers"}}]}
                           api-key owner)]
        (is (= organization-id (:organization/id data))))

      (testing "owners can see it"
        (let [data (api-call :get (str "/api/organizations")
                             nil
                             api-key org-owner1)]
          (is (contains? (set (map :organization/id data)) organization-id))
          (is (= {:organization/id organization-id
                  :organization/name {:fi "Organisaatiot API Test ORG"
                                      :en "Organizations API Test ORG"}
                  :organization/short-name {:fi "ORG" :en "ORG"}
                  :organization/owners [{:userid org-owner1 :email "organization-owner1@example.com" :name "Organization Owner 1"}]
                  :organization/review-emails [{:email "test@organization.test.org"
                                                :name {:fi "Organisaatiot API Test ORG Katselmoijat"
                                                       :en "Organizations API Test ORG Reviewers"}}]
                  :enabled true
                  :archived false}
                 (find-first (comp #{organization-id} :organization/id) (get-orgs owner))
                 (get-org owner organization-id)
                 (find-first (comp #{organization-id} :organization/id) (get-orgs org-owner1))
                 (get-org org-owner1 organization-id)))))

      (testing "a normal user can see it"
        (is (= {:organization/id organization-id
                :organization/name {:fi "Organisaatiot API Test ORG"
                                    :en "Organizations API Test ORG"}
                :organization/short-name {:fi "ORG" :en "ORG"}}
               (find-first (comp #{organization-id} :organization/id) (get-orgs user))
               (get-org user organization-id))))

      (testing "organization owner owns it"
        (let [data (api-call :get (str "/api/organizations?owner=" org-owner1)
                             nil
                             api-key org-owner1)]
          (is (contains? (set (map :organization/id data)) organization-id)))))

    (testing "edit organization"
      (testing "as owner"
        (let [data (api-call :put "/api/organizations/edit"
                             {:organization/id organization-id
                              :organization/name {:fi "Organisaatiot API Test ORG 2"
                                                  :en "Organizations API Test ORG 2"}
                              :organization/short-name {:fi "ORG2" :en "ORG2"}
                              :organization/owners [{:userid org-owner1} {:userid org-owner2} {:userid owner}]
                              :organization/review-emails [{:email "test@organization2.test.org"
                                                            :name {:fi "Organisaatiot 2 API Test ORG Katselmoijat"
                                                                   :en "Organizations 2 API Test ORG Reviewers"}}]}
                             api-key owner)]
          (is (= organization-id (:organization/id data)))
          (is (= {:organization/id organization-id
                  :organization/name {:fi "Organisaatiot API Test ORG 2"
                                      :en "Organizations API Test ORG 2"}
                  :organization/short-name {:fi "ORG2" :en "ORG2"}
                  :organization/owners [{:userid org-owner1 :email "organization-owner1@example.com" :name "Organization Owner 1"}
                                        {:userid org-owner2 :email "organization-owner2@example.com" :name "Organization Owner 2"}
                                        {:userid owner :email "owner@example.com" :name "Owner"}]
                  :organization/review-emails [{:email "test@organization2.test.org"
                                                :name {:fi "Organisaatiot 2 API Test ORG Katselmoijat"
                                                       :en "Organizations 2 API Test ORG Reviewers"}}]
                  :enabled true
                  :archived false}
                 (find-first (comp #{organization-id} :organization/id) (get-orgs owner))
                 (get-org owner organization-id))))
        (testing "that is also an organization owner can edit"
          (api-call :put "/api/organizations/edit"
                    {:organization/id organization-id
                     :organization/name {:fi "Organisaatiot API Test ORG 2"
                                         :en "Organizations API Test ORG 2"}
                     :organization/short-name {:fi "ORG2" :en "ORG2"}
                     :organization/owners [{:userid org-owner1} {:userid org-owner2}]
                     :organization/review-emails [{:email "test@organization2.test.org"
                                                   :name {:fi "Organisaatiot 2 API Test ORG Katselmoijat"
                                                          :en "Organizations 2 API Test ORG Reviewers"}}]}
                    api-key owner)
          (is (= {:organization/id organization-id
                  :organization/name {:fi "Organisaatiot API Test ORG 2"
                                      :en "Organizations API Test ORG 2"}
                  :organization/short-name {:fi "ORG2" :en "ORG2"}
                  :organization/owners [{:userid org-owner1 :email "organization-owner1@example.com" :name "Organization Owner 1"}
                                        {:userid org-owner2 :email "organization-owner2@example.com" :name "Organization Owner 2"}]
                  :organization/review-emails [{:email "test@organization2.test.org"
                                                :name {:fi "Organisaatiot 2 API Test ORG Katselmoijat"
                                                       :en "Organizations 2 API Test ORG Reviewers"}}]
                  :enabled true
                  :archived false}
                 (find-first (comp #{organization-id} :organization/id) (get-orgs owner))
                 (get-org owner organization-id)))))

      (testing "as organization-owner"
        (let [data (api-call :put "/api/organizations/edit"
                             {:organization/id organization-id
                              :organization/name {:fi "Organisaatiot API Test ORG 3"
                                                  :en "Organizations API Test ORG 3"}
                              :organization/short-name {:fi "ORG3" :en "ORG3"}
                              :organization/owners [{:userid org-owner2}]
                              :organization/review-emails [{:email "test@organization3.test.org"
                                                            :name {:fi "Organisaatiot 3 API Test ORG Katselmoijat"
                                                                   :en "Organizations 3 API Test ORG Reviewers"}}]}
                             api-key org-owner1)]
          (is (= organization-id (:organization/id data)))
          (is (= {:organization/id organization-id
                  :organization/name {:fi "Organisaatiot API Test ORG 3"
                                      :en "Organizations API Test ORG 3"}
                  :organization/short-name {:fi "ORG3" :en "ORG3"}
                  :organization/owners [{:userid org-owner2 :email "organization-owner2@example.com" :name "Organization Owner 2"}]
                  :organization/review-emails [{:email "test@organization3.test.org"
                                                :name {:fi "Organisaatiot 3 API Test ORG Katselmoijat"
                                                       :en "Organizations 3 API Test ORG Reviewers"}}]
                  :enabled true
                  :archived false}
                 (find-first (comp #{organization-id} :organization/id) (get-orgs owner))
                 (get-org owner organization-id)))

          (testing "unable to edit organization owners when no longer organization owner"
          ;; XXX: org owner loses the role #{:organization-owner} if user is not
          ;; organization owner in any org. hence we create a second org which
          ;; maintains this role.
            (api-call :post "/api/organizations/create"
                      {:organization/id organization-id-2
                       :organization/name {:fi "Organisaatiot API Test ORG"
                                           :en "Organizations API Test ORG"}
                       :organization/short-name {:fi "ORG" :en "ORG"}
                       :organization/owners [{:userid org-owner1}]
                       :organization/review-emails []}
                      api-key owner)
            (let [org-data {:organization/id organization-id
                            :organization/name {:fi "Organisaatiot API Test ORG 3"
                                                :en "Organizations API Test ORG 3"}
                            :organization/short-name {:fi "ORG3" :en "ORG3"}
                            :organization/owners [{:userid org-owner2}]
                            :organization/review-emails [{:email "test@organization3.test.org"
                                                          :name {:fi "Organisaatiot 3 API Test ORG Katselmoijat"
                                                                 :en "Organizations 3 API Test ORG Reviewers"}}]}]
              (is (response-is-forbidden?
                   (api-response :put "/api/organizations/edit"
                                 (assoc org-data :organization/name {:fi "Ei mene läpi"})
                                 api-key org-owner1)))
              (is (response-is-forbidden?
                   (api-response :put "/api/organizations/edit"
                                 (assoc org-data :organization/owners [{:userid org-owner1}])
                                 api-key org-owner1))))
            (is (= {:organization/id organization-id
                    :organization/name {:fi "Organisaatiot API Test ORG 3"
                                        :en "Organizations API Test ORG 3"}
                    :organization/short-name {:fi "ORG3" :en "ORG3"}
                    :organization/owners [{:userid org-owner2 :email "organization-owner2@example.com" :name "Organization Owner 2"}]
                    :organization/review-emails [{:email "test@organization3.test.org"
                                                  :name {:fi "Organisaatiot 3 API Test ORG Katselmoijat"
                                                         :en "Organizations 3 API Test ORG Reviewers"}}]
                    :enabled true
                    :archived false}
                   (find-first (comp #{organization-id} :organization/id) (get-orgs owner))
                   (get-org owner organization-id))
                "organization data is unchanged")))))))

(deftest organization-api-status-test
  (api-key/add-api-key! "42")
  (test-helpers/create-user! {:userid "owner" :name "Owner" :email "owner@example.com"} :owner)
  (api-call :post "/api/organizations/create"
            {:organization/id organization-id
             :organization/name {:fi "Organisaatiot API Test ORG"
                                 :en "Organizations API Test ORG"}
             :organization/short-name {:fi "ORG" :en "ORG"}
             :organization/owners []
             :organization/review-emails []}
            "42" "owner")
  (let [get-status (fn [] (-> (api-call :get (str "/api/organizations/organizations-api-test-org")
                                        nil
                                        "42" "owner")
                              (select-keys [:enabled :archived])))]
    (is (= {:enabled true :archived false} (get-status)) "initially enabled, not archived")
    (api-call :put "/api/organizations/enabled"
              {:organization/id organization-id
               :enabled false}
              "42" "owner")
    (is (= {:enabled false :archived false} (get-status)))
    (api-call :put "/api/organizations/archived"
              {:organization/id organization-id
               :archived true}
              "42" "owner")
    (is (= {:enabled false :archived true} (get-status)))
    (api-call :put "/api/organizations/enabled"
              {:organization/id organization-id
               :enabled true}
              "42" "owner")
    (is (= {:enabled true :archived true} (get-status)))
    (api-call :put "/api/organizations/archived"
              {:organization/id organization-id
               :archived false}
              "42" "owner")
    (is (= {:enabled true :archived false} (get-status)))))

(deftest organizations-api-security-test
  (api-key/add-api-key! "42")
  (test-helpers/create-user! {:userid "alice"})
  (test-helpers/create-user! {:userid "owner"} :owner)
  (testing "without authentication"
    (testing "list"
      (let [response (api-response :get "/api/organizations")]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))

    (testing "create"
      (let [response (api-response :post "/api/organizations/create"
                                   {:organization/id "test-organization"
                                    :organization/name {:fi "Testiorganisaatio"
                                                        :en "Test Organization"}
                                    :organization/short-name {:fi "ORG"
                                                              :en "ORG"}
                                    :organization/owners []
                                    :organization/review-emails []})]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (api-call :get "/api/organizations"
                               nil
                               "42" "alice")]
        (is (not-any? #{:organization/owners :organization/review-emails}
                      (mapcat keys response))
            "can't see all the attributes")))

    (testing "create"
      (let [response (api-response :post "/api/organizations/create"
                                   {:organization/id "test-organization"
                                    :organization/name {:fi "Testiorganisaatio"
                                                        :en "Test Organization"}
                                    :organization/short-name {:fi "ORG"
                                                              :en "ORG"}
                                    :organization/owners []
                                    :organization/review-emails []}
                                   "42" "alice")]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response))))))

  (testing "given an organization"
    (api-call :post "/api/organizations/create"
              {:organization/id "test-organization"
               :organization/name {:fi "Testiorganisaatio"
                                   :en "Test Organization"}
               :organization/short-name {:fi "ORG"
                                         :en "ORG"}
               :organization/owners []
               :organization/review-emails []}
              "42" "owner")
    (testing "edit"
      (let [response (api-response :put "/api/organizations/edit"
                                   {:organization/id "test-organization"
                                    :organization/name {:fi "Testiorganisaatio"
                                                        :en "Test Organization"}
                                    :organization/short-name {:fi "ORG"
                                                              :en "ORG"}
                                    :organization/owners []
                                    :organization/review-emails []}
                                   "42" "alice")]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response))))
      (let [response (api-response :put "/api/organizations/edit"
                                   {:organization/id "test-organization"
                                    :organization/name {:fi "Testiorganisaatio"
                                                        :en "Test Organization"}
                                    :organization/short-name {:fi "ORG"
                                                              :en "ORG"}
                                    :organization/owners []
                                    :organization/review-emails []}
                                   "42" "organization-owner")]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response))))
      (testing "given organization-owner role"
        (api-call :put "/api/organizations/edit"
                  {:organization/id "test-organization"
                   :organization/name {:fi "Testiorganisaatio"
                                       :en "Test Organization"}
                   :organization/short-name {:fi "ORG"
                                             :en "ORG"}
                   :organization/owners [{:userid "organization-owner1"}]
                   :organization/review-emails []}
                  "42" "owner")
        (api-call :put "/api/organizations/edit"
                  {:organization/id "test-organization"
                   :organization/name {:fi "Testiorganisaatio"
                                       :en "Test Organization"}
                   :organization/short-name {:fi "ORG"
                                             :en "ORG"}
                   :organization/owners [{:userid "organization-owner1"}]
                   :organization/review-emails []}
                  "42" "organization-owner1")))))

(deftest organization-duplicate-key-test
  (let [api-key "42"
        owner "owner"
        org-owner1 "organization-owner1"]
    (api-key/add-api-key! api-key)
    (test-helpers/create-user! {:userid owner} :owner)
    (test-helpers/create-user! {:userid org-owner1})
    (testing "trying to create a duplicate fails" ; separate test because it will leave the transaction in an errored state
      (let [_response1 (api-call :post "/api/organizations/create"
                                 {:organization/id "duplicate-organizations-api-test-org"
                                  :organization/name {:fi "Duplikaatti Organisaatiot API Test ORG"
                                                      :en "Duplicate Organizations API Test ORG"}
                                  :organization/short-name {:fi "DUPORG" :en "DUPORG"}
                                  :organization/owners [{:userid org-owner1}]
                                  :organization/review-emails [{:email "test@organization.test.org"
                                                                :name {:fi "Duplikaatti Organisaatiot API Test ORG Katselmoijat"
                                                                       :en "Duplicate Organizations API Test ORG Reviewers"}}]}
                                 api-key owner)
            response2 (api-call :post "/api/organizations/create"
                                {:organization/id "duplicate-organizations-api-test-org"
                                 :organization/name {:fi "Duplikaatti Organisaatiot API Test ORG"
                                                     :en "Duplicate Organizations API Test ORG"}
                                 :organization/short-name {:fi "DUPORG" :en "DUPORG"}
                                 :organization/owners [{:userid org-owner1}]
                                 :organization/review-emails [{:email "test@organization.test.org"
                                                               :name {:fi "Duplikaatti Organisaatiot API Test ORG Katselmoijat"
                                                                      :en "Duplicate Organizations API Test ORG Reviewers"}}]}
                                api-key owner)]
        (is (= {:success false
                :errors [{:type "t.actions.errors/duplicate-id"
                          :organization/id "duplicate-organizations-api-test-org"}]}
               response2))))))
