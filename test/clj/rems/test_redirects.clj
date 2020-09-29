(ns ^:integration rems.test-redirects
  (:require [clojure.test :refer :all]
            [rems.api.services.attachment :as attachment]
            [rems.api.services.licenses :as licenses]
            [rems.api.testing :refer :all]
            [rems.db.core :as db]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture
  (fn [f]
    ;; need to set an explicit public-url since dev and test configs use different ports
    (with-redefs [rems.config/env (assoc rems.config/env :public-url "https://public.url/")]
      (f))))

(defn disable-catalogue-item [catid]
  (db/set-catalogue-item-enabled! {:id catid :enabled false}))

(deftest test-redirect-to-new-application
  (testing "redirects to new application page for catalogue item matching the resource ID"
    (let [resid (test-helpers/create-resource! {:resource-ext-id "urn:one-matching-resource"})
          catid (test-helpers/create-catalogue-item! {:resource-id resid})
          response (-> (request :get "/apply-for?resource=urn:one-matching-resource")
                       handler)]
      (is (= 302 (:status response)))
      (is (= (str "https://public.url/application?items=" catid) (get-in response [:headers "Location"])))))

  (testing "specifying multiple resources"
    (let [wf (test-helpers/create-workflow! {})
          resid-1 (test-helpers/create-resource! {:resource-ext-id "urn:multiple1"})
          catid-1 (test-helpers/create-catalogue-item! {:resource-id resid-1 :workflow-id wf})
          resid-2 (test-helpers/create-resource! {:resource-ext-id "urn:multiple2"})
          catid-2 (test-helpers/create-catalogue-item! {:resource-id resid-2 :workflow-id wf})
          resid-3 (test-helpers/create-resource! {:resource-ext-id "urn:multiple3"})
          catid-3 (test-helpers/create-catalogue-item! {:resource-id resid-3 :workflow-id nil})] ;; create fresh workflow
      (testing "works when workflows match"
        (let [response (-> (request :get "/apply-for?resource=urn:multiple1&resource=urn:multiple2")
                           handler)]
          (is (= 302 (:status response)))
          (is (= (str "https://public.url/application?items=" catid-1 "," catid-2)
                 (get-in response [:headers "Location"])))))
      (testing "fails if workflows differ"
        (let [response (-> (request :get "/apply-for?resource=urn:multiple1&resource=urn:multiple2&resource=urn:multiple3")
                           handler)]
          (is (= 400 (:status response)))
          (is (.startsWith (read-body response) "Unbundlable"))))))

  (testing "fails if no catalogue item is found"
    (let [response (-> (request :get "/apply-for?resource=urn:no-such-resource")
                       handler)]
      (is (= 404 (:status response)))
      (is (= "Resource not found" (read-body response))))
    (testing "even if multiple resources specified"
      (let [response (-> (request :get "/apply-for?resource=urn:no-such-resource&resource=urn:one-matching-resource")
                         handler)]
        (is (= 404 (:status response)))
        (is (= "Resource not found" (read-body response))))))

  (testing "fails if more than one catalogue item is found"
    (let [resid (test-helpers/create-resource! {:resource-ext-id "urn:two-matching-resources"})
          _ (test-helpers/create-catalogue-item! {:resource-id resid})
          _ (test-helpers/create-catalogue-item! {:resource-id resid})
          response (-> (request :get "/apply-for?resource=urn:two-matching-resources")
                       handler)]
      (is (= 400 (:status response)))
      (is (= "Catalogue item is not unique" (read-body response)))
      (testing "even if multiple resources are specified"
        (let [response (-> (request :get "/apply-for?resource=urn:two-matching-resources&resource=urn:one-matching-resource")
                           handler)]
          (is (= 400 (:status response)))
          (is (= "Catalogue item is not unique" (read-body response)))))))

  (testing "redirects to active catalogue item, ignoring disabled items for the same resource ID"
    (let [resid (test-helpers/create-resource! {:resource-ext-id "urn:enabled-and-disabled-items"})
          old-catid (test-helpers/create-catalogue-item! {:resource-id resid})
          _ (disable-catalogue-item old-catid)
          new-catid (test-helpers/create-catalogue-item! {:resource-id resid})
          response (-> (request :get "/apply-for?resource=urn:enabled-and-disabled-items")
                       handler)]
      (is (= 302 (:status response)))
      (is (= (str "https://public.url/application?items=" new-catid) (get-in response [:headers "Location"]))))))

(def dummy-attachment {:application/id 123
                       :attachment/filename "file.txt"
                       :attachment/data (.getBytes "file content")
                       :attachment/type "text/plain"})

(deftest test-attachment-download
  (with-redefs [attachment/get-application-attachment (fn [& args]
                                                        (is (= ["alice" 123] args))
                                                        dummy-attachment)]
    (testing "download attachment when logged in"
      (let [response (-> (request :get "/applications/attachment/123")
                         (authenticate "42" "alice")
                         handler)]
        (is (= 200 (:status response)))
        (is (= "file content" (slurp (:body response))))))

    (testing "redirect to login when logged out"
      (let [response (-> (request :get "/applications/attachment/123")
                         handler)]
        (is (= 302 (:status response)))
        (is (= "https://public.url/?redirect=%2Fapplications%2Fattachment%2F123"
               (get-in response [:headers "Location"]))))))

  (testing "attachment not found"
    (with-redefs [attachment/get-application-attachment (constantly nil)]
      (let [response (-> (request :get "/applications/attachment/123")
                         (authenticate "42" "alice")
                         handler)]
        (is (= 404 (:status response)))
        (is (= "not found" (:body response)))))))

(deftest test-license-attachment-download
  (with-redefs [licenses/get-application-license-attachment (fn [& args]
                                                              (is (= ["alice" 1023 3 :en] args))
                                                              dummy-attachment)]
    (testing "download attachment when logged in"
      (let [response (-> (request :get "/applications/1023/license-attachment/3/en")
                         (authenticate "42" "alice")
                         handler)]
        (is (= 200 (:status response)))
        (is (= "file content" (slurp (:body response))))))

    (testing "redirect to login when logged out"
      (let [response (-> (request :get "/applications/1023/license-attachment/3/en")
                         handler)]
        (is (= 302 (:status response)))
        (is (= "https://public.url/?redirect=%2Fapplications%2F1023%2Flicense-attachment%2F3%2Fen"
               (get-in response [:headers "Location"]))))))

  (testing "attachment not found"
    (with-redefs [licenses/get-application-license-attachment (constantly nil)]
      (let [response (-> (request :get "/applications/1023/license-attachment/3/en")
                         (authenticate "42" "alice")
                         handler)]
        (is (= 404 (:status response)))
        (is (= "not found" (:body response)))))))
