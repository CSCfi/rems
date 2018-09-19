(ns ^:integration rems.test.api.public
  (:require [clojure.test :refer :all]
            [rems.handler :refer :all]
            [rems.test.api :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest service-translations-test
  (let [api-key "42"
        user-id "alice"]
    (let [data (-> (request :get "/api/translations")
                   (authenticate api-key user-id)
                   app
                   read-body)
          languages (keys data)]
      (is (= [:en :en-GB :fi] (sort languages))))))

(deftest entitlements-test
  (let [api-key "42"
        user-id "developer"]
    (testing "all"
      (let [data (-> (request :get "/api/entitlements")
                     (authenticate api-key user-id)
                     app
                     read-body)]
        (is (= 3 (count data)))))
    (testing "just for alice"
      (let [data (-> (request :get "/api/entitlements?user=alice")
                     (authenticate api-key user-id)
                     app
                     read-body)]
        (is (= 1 (count data))))))
  (testing "listing as applicant"
    (testing "with entitlements"
      (let [response (-> (request :get (str "/api/entitlements"))
                         (authenticate "42" "alice")
                         app)
            body (read-body response)]
        (is (response-is-ok? response))
        (is (coll-is-not-empty? body))
        (is (every? #(= (:mail %) "a@li.ce") body))))
    (testing "without entitlements"
      (let [response (-> (request :get (str "/api/entitlements"))
                         (authenticate "42" "carl")
                         app)
            body (read-body response)]
        (is (response-is-ok? response))
        (is (coll-is-empty? body))))))
