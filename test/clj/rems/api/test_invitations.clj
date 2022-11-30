(ns ^:integration rems.api.test-invitations
  (:require [clojure.test :refer :all]
            [rems.service.invitation :as invitation]
            [rems.api.testing :refer :all]
            [rems.db.applications]
            [rems.service.test-data :as test-data]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.json]
            [rems.testing-util :refer [with-user]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :each
  api-fixture
  ;; TODO should this fixture have a name?
  (fn [f]
    (test-data/create-test-api-key!)
    (test-data/create-test-users-and-roles!)
    (f)))

(deftest test-invitation-flow
  (let [workflow-id (test-helpers/create-workflow! {})]
    (testing "before invitation"
      (is (= [] (api-call :get "/api/invitations" nil test-data/+test-api-key+ "owner"))))

    (testing "create invitation"
      (let [body (api-call :post "/api/invitations/create" {:name "Katherine Johnson"
                                                            :email "katherine.johnson@nasa.gov"
                                                            :workflow-id workflow-id}
                           test-data/+test-api-key+ "owner")]
        (is (= {:success true}
               (dissoc body :invitation/id)))))

    (testing "after invitation"
      (is (= [] (api-call :get "/api/invitations?sent=false" nil test-data/+test-api-key+ "owner"))
          "invitation has already been sent")
      (is (= [] (api-call :get "/api/invitations?accepted=true" nil test-data/+test-api-key+ "owner"))
          "invitation has not been accepted")

      (let [invitation (first (api-call :get "/api/invitations" nil test-data/+test-api-key+ "owner"))
            invitation2 (first (api-call :get "/api/invitations?sent=true" nil test-data/+test-api-key+ "owner"))]
        (is (= {:invitation/name "Katherine Johnson"
                :invitation/email "katherine.johnson@nasa.gov"
                :invitation/invited-by {:email "owner@example.com"
                                        :userid "owner"
                                        :name "Owner"}
                :invitation/workflow {:workflow/id workflow-id}}
               (dissoc invitation
                       :invitation/id ; always different from sequence
                       :invitation/created ; should be checked in service
                       :invitation/sent)
               (dissoc invitation2
                       :invitation/id ; always different from sequence
                       :invitation/created ; should be checked in service
                       :invitation/sent)))   ; should be checked in service

        (testing "accepting"
          (let [token (:invitation/token (invitation/get-invitation-full (:invitation/id invitation)))] ; not available in API
            (is (= {:success true
                    :invitation/workflow {:workflow/id workflow-id}}
                   (api-call :post "/api/invitations/accept-invitation" {:token token} test-data/+test-api-key+ "katherine"))))
          (test-helpers/create-user! {:userid "katherine" :email "katherine.johnson@nasa.gov" :name "Katherine Johnson"})

          (let [accepted-invitation (first (api-call :get "/api/invitations" nil test-data/+test-api-key+ "owner"))]
            (is (= {:invitation/name "Katherine Johnson"
                    :invitation/email "katherine.johnson@nasa.gov"
                    :invitation/invited-by {:email "owner@example.com"
                                            :userid "owner"
                                            :name "Owner"}
                    :invitation/invited-user {:email "katherine.johnson@nasa.gov"
                                              :userid "katherine"
                                              :name "Katherine Johnson"}
                    :invitation/workflow {:workflow/id workflow-id}}
                   (dissoc accepted-invitation :invitation/id :invitation/created :invitation/sent :invitation/accepted)))))))))

(deftest test-invitations-api-security
  (api-call :post "/api/organizations/create"
            {:organization/id "test-organization"
             :organization/name {:fi "Testiorganisaatio"
                                 :en "Test Organization"}
             :organization/short-name {:fi "ORG"
                                       :en "ORG"}
             :organization/owners [{:userid "organization-owner2"}]
             :organization/review-emails []}
            "42" "owner")
  (test-helpers/create-user! {:userid "katherine" :email "katherine.johnson@nasa.gov" :name "Katherine Johnson"})
  (let [workflow-id (test-helpers/create-workflow! {:organization {:organization/id "test-organization"}})
        invitation-id (:invitation/id (with-user "owner"
                                        (invitation/create-invitation! {:userid "owner"
                                                                        :name "Katherine Johnson"
                                                                        :email "katherine.johnson@nasa.gov"
                                                                        :workflow-id workflow-id})))
        token (:invitation/token (with-user "owner" (invitation/get-invitation-full invitation-id)))]
    (testing "without authentication"
      (testing "list"
        (let [response (api-response :get "/api/invitations")]
          (is (response-is-unauthorized? response))
          (is (= "unauthorized" (read-body response)))))

      (testing "create"
        (let [response (api-response :post "/api/invitations/create"
                                     {:name "Katherine Johnson"
                                      :email "katherine.johnson@nasa.gov"
                                      :workflow-id workflow-id})]
          (is (response-is-unauthorized? response))
          (is (= "Invalid anti-forgery token" (read-body response)))))

      (testing "accept-invitation"
        (let [response (api-response :post (str "/api/invitations/accept-invitation?token=" token) nil)]
          (is (response-is-unauthorized? response))
          (is (= "Invalid anti-forgery token" (read-body response))))))

    (testing "create"
      (testing "without owner role"
        (let [response (api-response :post "/api/invitations/create"
                                     {:name "Katherine Johnson"
                                      :email "katherine.johnson@nasa.gov"
                                      :workflow-id workflow-id}
                                     "42" "alice")]
          (is (response-is-forbidden? response))
          (is (= "forbidden" (read-body response)))))

      (testing "for an owner of wrong organization"
        (let [response (api-response :post "/api/invitations/create"
                                     {:name "Katherine Johnson"
                                      :email "katherine.johnson@nasa.gov"
                                      :workflow-id workflow-id}
                                     "42" "organization-owner1")]
          (is (response-is-forbidden? response))
          (is (= "forbidden" (read-body response)))))

      (testing "given owner role"
        (api-call :post "/api/invitations/create"
                  {:name "Katherine Johnson"
                   :email "katherine.johnson@nasa.gov"
                   :workflow-id workflow-id}
                  "42" "owner"))

      (testing "given organization-owner role"
        (api-call :post "/api/invitations/create"
                  {:name "Katherine Johnson"
                   :email "katherine.johnson@nasa.gov"
                   :workflow-id workflow-id}
                  "42" "organization-owner2")))

    (testing "accept-invitation"
      (let [response (api-response :post (str "/api/invitations/accept-invitation?token=" token) nil "42" "katherine")]
        (is (response-is-ok? response))))))
