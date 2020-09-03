(ns ^:integration rems.api.test-organizations
  (:require [clojure.test :refer :all]
            [medley.core :refer [find-first update-existing]]
            [rems.api.testing :refer :all]
            [rems.testing-util :refer [fixed-time-fixture]]
            [ring.mock.request :refer :all])
  (:import [org.joda.time DateTime DateTimeZone DateTimeUtils]))

(def test-time1 (DateTime. 10000 DateTimeZone/UTC))
(def test-time2 (DateTime. 20000 DateTimeZone/UTC))
(def test-time3 (DateTime. 30000 DateTimeZone/UTC))

(use-fixtures
  :each
  api-fixture
  (fixed-time-fixture test-time1))

(deftest organizations-api-test
  (let [api-key "42"
        user "alice"
        owner "owner"
        org-owner1 "organization-owner1"
        org-owner2 "organization-owner2"
        get-org (fn [userid id] (-> (api-call :get (str "/api/organizations/" id)
                                              nil
                                              api-key userid)
                                    (update-existing :organization/last-modified parse-date)))
        get-orgs (fn [userid] (->> (api-call :get (str "/api/organizations")
                                             nil
                                             api-key userid)
                                   (map #(update-existing % :organization/last-modified parse-date))))]

    (testing "finds test data"
      (let [data (api-call :get "/api/organizations"
                           nil
                           api-key owner)]
        (is (= #{"default" "abc" "hus" "thl" "csc" "nbn" "organization1" "organization2"}
               (set (map :organization/id data))))))

    (testing "create organization"
      (let [data (api-call :post "/api/organizations/create"
                           {:organization/id "organizations-api-test-org"
                            :organization/name {:fi "Organisaatiot API Test ORG"
                                                :en "Organizations API Test ORG"}
                            :organization/short-name {:fi "ORG" :en "ORG"}
                            :organization/owners [{:userid org-owner1}]
                            :organization/review-emails [{:email "test@organization.test.org"
                                                          :name {:fi "Organisaatiot API Test ORG Katselmoijat"
                                                                 :en "Organizations API Test ORG Reviewers"}}]}
                           api-key owner)]
        (is (= "organizations-api-test-org" (:organization/id data))))

      (testing "owners can see it"
        (let [data (api-call :get (str "/api/organizations")
                             nil
                             api-key org-owner1)]
          (is (contains? (set (map :organization/id data)) "organizations-api-test-org"))
          (is (= {:organization/id "organizations-api-test-org"
                  :organization/name {:fi "Organisaatiot API Test ORG"
                                      :en "Organizations API Test ORG"}
                  :organization/short-name {:fi "ORG" :en "ORG"}
                  :organization/owners [{:userid org-owner1}]
                  :organization/last-modified test-time1
                  :organization/modifier {:userid owner}
                  :organization/review-emails [{:email "test@organization.test.org"
                                                :name {:fi "Organisaatiot API Test ORG Katselmoijat"
                                                       :en "Organizations API Test ORG Reviewers"}}]
                  :enabled true
                  :archived false}
                 (find-first (comp #{"organizations-api-test-org"} :organization/id) (get-orgs owner))
                 (get-org owner "organizations-api-test-org")
                 (find-first (comp #{"organizations-api-test-org"} :organization/id) (get-orgs org-owner1))
                 (get-org org-owner1 "organizations-api-test-org")))))

      (testing "a normal user can see it"
        (is (= {:organization/id "organizations-api-test-org"
                :organization/name {:fi "Organisaatiot API Test ORG"
                                    :en "Organizations API Test ORG"}
                :organization/short-name {:fi "ORG" :en "ORG"}}
               (find-first (comp #{"organizations-api-test-org"} :organization/id) (get-orgs user))
               (get-org user "organizations-api-test-org"))))

      (testing "organization owner owns it"
        (let [data (api-call :get (str "/api/organizations?owner=" org-owner1)
                             nil
                             api-key org-owner1)]
          (is (contains? (set (map :organization/id data)) "organizations-api-test-org")))))

    (testing "edit organization"
      (testing "as owner"
        (DateTimeUtils/setCurrentMillisFixed (.getMillis test-time2))
        (let [data (api-call :put "/api/organizations/edit"
                             {:organization/id "organizations-api-test-org"
                              :organization/name {:fi "Organisaatiot API Test ORG 2"
                                                  :en "Organizations API Test ORG 2"}
                              :organization/short-name {:fi "ORG2" :en "ORG2"}
                              :organization/owners [{:userid org-owner1} {:userid org-owner2}]
                              :organization/review-emails [{:email "test@organization2.test.org"
                                                            :name {:fi "Organisaatiot 2 API Test ORG Katselmoijat"
                                                                   :en "Organizations 2 API Test ORG Reviewers"}}]}
                             api-key owner)]
          (is (= "organizations-api-test-org" (:organization/id data)))
          (is (= {:organization/id "organizations-api-test-org"
                  :organization/name {:fi "Organisaatiot API Test ORG 2"
                                      :en "Organizations API Test ORG 2"}
                  :organization/short-name {:fi "ORG2" :en "ORG2"}
                  :organization/owners [{:userid org-owner1} {:userid org-owner2}]
                  :organization/last-modified test-time2
                  :organization/modifier {:userid owner}
                  :organization/review-emails [{:email "test@organization2.test.org"
                                                :name {:fi "Organisaatiot 2 API Test ORG Katselmoijat"
                                                       :en "Organizations 2 API Test ORG Reviewers"}}]
                  :enabled true
                  :archived false}
                 (find-first (comp #{"organizations-api-test-org"} :organization/id) (get-orgs owner))
                 (get-org owner "organizations-api-test-org")))))

      (testing "as organization-owner"
        (DateTimeUtils/setCurrentMillisFixed (.getMillis test-time3))
        (let [data (api-call :put "/api/organizations/edit"
                             {:organization/id "organizations-api-test-org"
                              :organization/name {:fi "Organisaatiot API Test ORG 3"
                                                  :en "Organizations API Test ORG 3"}
                              :organization/short-name {:fi "ORG3" :en "ORG3"}
                              :organization/owners [{:userid org-owner2}]
                              :organization/review-emails [{:email "test@organization3.test.org"
                                                            :name {:fi "Organisaatiot 3 API Test ORG Katselmoijat"
                                                                   :en "Organizations 3 API Test ORG Reviewers"}}]}
                             api-key org-owner1)]
          (is (= "organizations-api-test-org" (:organization/id data)))
          (is (= {:organization/id "organizations-api-test-org"
                  :organization/name {:fi "Organisaatiot API Test ORG 3"
                                      :en "Organizations API Test ORG 3"}
                  :organization/short-name {:fi "ORG3" :en "ORG3"}
                  :organization/owners [{:userid org-owner1} {:userid org-owner2}] ; owners is not changed
                  :organization/last-modified test-time3
                  :organization/modifier {:userid org-owner1}
                  :organization/review-emails [{:email "test@organization3.test.org"
                                                :name {:fi "Organisaatiot 3 API Test ORG Katselmoijat"
                                                       :en "Organizations 3 API Test ORG Reviewers"}}]
                  :enabled true
                  :archived false}
                 (find-first (comp #{"organizations-api-test-org"} :organization/id) (get-orgs owner))
                 (get-org owner "organizations-api-test-org"))))))))

(deftest organization-api-status-test
  (api-call :post "/api/organizations/create"
            {:organization/id "organizations-api-test-org"
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
              {:organization/id "organizations-api-test-org"
               :enabled false}
              "42" "owner")
    (is (= {:enabled false :archived false} (get-status)))
    (api-call :put "/api/organizations/archived"
              {:organization/id "organizations-api-test-org"
               :archived true}
              "42" "owner")
    (is (= {:enabled false :archived true} (get-status)))
    (api-call :put "/api/organizations/enabled"
              {:organization/id "organizations-api-test-org"
               :enabled true}
              "42" "owner")
    (is (= {:enabled true :archived true} (get-status)))
    (api-call :put "/api/organizations/archived"
              {:organization/id "organizations-api-test-org"
               :archived false}
              "42" "owner")
    (is (= {:enabled true :archived false} (get-status)))))

(deftest organizations-api-security-test
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
        (is (= "forbidden" (read-body response))))

      (api-call :post "/api/organizations/create"
              {:organization/id "test-organization"
               :organization/name {:fi "Testiorganisaatio"
                                   :en "Test Organization"}
               :organization/short-name {:fi "ORG"
                                         :en "ORG"}
               :organization/owners []
               :organization/review-emails []}
              "42" "owner"))
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
      (testing "success after rights given"
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
