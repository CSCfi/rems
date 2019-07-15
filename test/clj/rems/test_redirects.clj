(ns ^:integration rems.test-redirects
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.resource :as resource]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(def test-user {:eppn "test-user"})

(defn dummy-resource [resid]
  (:id (resource/create-resource! {:resid resid
                                   :organization "abc"
                                   :licenses []}
                                  (:eppn test-user))))

(defn dummy-catalogue-item [resid]
  (:id (catalogue/create-catalogue-item! {:title ""
                                          :form 1
                                          :resid resid
                                          :wfid 1})))

(defn disable-catalogue-item [catid]
  (db/set-catalogue-item-state! {:id catid :enabled false}))

(deftest redirect-to-new-application-test
  (testing "redirects to new application page for catalogue item matching the resource ID"
    (let [resid (dummy-resource "urn:one-matching-resource")
          catid (dummy-catalogue-item resid)
          response (-> (request :get "/apply-for?resource=urn:one-matching-resource")
                       handler)]
      (is (= 302 (:status response)))
      (is (= (str "http://localhost/#/application?items=" catid) (get-in response [:headers "Location"])))))

  (testing "fails if no catalogue item is found"
    (let [response (-> (request :get "/apply-for?resource=urn:no-such-resource")
                       handler)]
      (is (= 404 (:status response)))
      (is (= "Resource not found" (read-body response)))))

  (testing "fails if more than one catalogue item is found"
    (let [resid (dummy-resource "urn:two-matching-resources")
          _ (dummy-catalogue-item resid)
          _ (dummy-catalogue-item resid)
          response (-> (request :get "/apply-for?resource=urn:two-matching-resources")
                       handler)]
      (is (= 404 (:status response)))
      (is (= "Resource ID is not unique" (read-body response)))))

  (testing "redirects to active catalogue item, ignoring disabled items for the same resource ID"
    (let [resid (dummy-resource "urn:enabed-and-disabled-items")
          old-catid (dummy-catalogue-item resid)
          _ (disable-catalogue-item old-catid)
          new-catid (dummy-catalogue-item resid)
          response (-> (request :get "/apply-for?resource=urn:enabed-and-disabled-items")
                       handler)]
      (is (= 302 (:status response)))
      (is (= (str "http://localhost/#/application?items=" new-catid) (get-in response [:headers "Location"]))))))
