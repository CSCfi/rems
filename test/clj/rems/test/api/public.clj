(ns rems.test.api.public
  (:require [clojure.test :refer :all]
            [rems.handler :refer :all]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
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
        (is (= 1 (count data)))))
    (testing "unauthorized"
      (let [resp (-> (request :get "/api/entitlements?user=alice")
                     (authenticate api-key "alice")
                     app)]
        (is (= 401 (:status resp)))))))
