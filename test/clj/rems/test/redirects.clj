(ns ^:integration rems.test.redirects
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.db.resource :as resource]
            [rems.db.catalogue :as catalogue]
            [rems.home :refer [home-routes]]
            [rems.test.api :refer :all]
            [ring.mock.request :refer :all]
            [rems.context :as context]))

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
      (is (= "Resource ID is not unique" (read-body response))))))
