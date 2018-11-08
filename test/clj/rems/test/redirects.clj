(ns ^:integration rems.test.redirects
  (:require [clojure.test :refer :all]
            [rems.context :as context]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.resource :as resource]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(def test-user {"eppn" "test-user"})

(defn dummy-resource [resid]
  (binding [context/*user* test-user]
    (:id (resource/create-resource! {:resid resid
                                     :organization "abc"
                                     :licenses []}))))

(defn dummy-catalogue-item [resid]
  (binding [context/*user* test-user]
    (:id (catalogue/create-catalogue-item! {:title ""
                                            :form 1
                                            :resid resid
                                            :wfid 1}))))

(defn disable-catalogue-item [catid]
  (db/set-catalogue-item-state! {:item catid :state "disabled"}))

(deftest redirect-to-new-application-test
  (testing "redirects to new application page for catalogue item matching the resource ID"
    (let [resid (dummy-resource "urn:one-matching-resource")
          catid (dummy-catalogue-item resid)
          response (-> (request :get "/apply-for?resource=urn:one-matching-resource")
                       app)]
      (is (= 302 (:status response)))
      (is (= (str "http://localhost/#/application?items=" catid) (get-in response [:headers "Location"])))))

  (testing "fails if no catalogue item is found"
    (let [response (-> (request :get "/apply-for?resource=urn:no-such-resource")
                       app)]
      (is (= 404 (:status response)))
      (is (= "Resource not found" (read-body response)))))

  (testing "fails if more than one catalogue item is found"
    (let [resid (dummy-resource "urn:two-matching-resources")
          _ (dummy-catalogue-item resid)
          _ (dummy-catalogue-item resid)
          response (-> (request :get "/apply-for?resource=urn:two-matching-resources")
                       app)]
      (is (= 404 (:status response)))
      (is (= "Resource ID is not unique" (read-body response)))))

  (testing "redirects to active catalogue item, ignoring disabled items for the same resource ID"
    (let [resid (dummy-resource "urn:enabed-and-disabled-items")
          old-catid (dummy-catalogue-item resid)
          _ (disable-catalogue-item old-catid)
          new-catid (dummy-catalogue-item resid)
          response (-> (request :get "/apply-for?resource=urn:enabed-and-disabled-items")
                       app)]
      (is (= 302 (:status response)))
      (is (= (str "http://localhost/#/application?items=" new-catid) (get-in response [:headers "Location"]))))))
