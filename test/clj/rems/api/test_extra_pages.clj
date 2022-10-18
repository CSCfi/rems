(ns ^:integration rems.api.test-extra-pages
  (:require [clojure.test :refer :all]
            [rems.db.test-data :as test-data]
            [rems.api.testing :refer :all]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest extra-pages-api-test
  (let [api-key "42"
        user-id "owner"]
    (test-data/create-test-users-and-roles!)
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :extra-pages [{:id "test"
                                                        :filename "test-en.md"
                                                        :translations {:fi {:title "Testi"
                                                                            :filename "test-fi.md"}
                                                                       :en {:title "Test"}
                                                                       :de {:url "https://example.org/de"}}}

                                                       {:id "test2"
                                                        :url "https://example.org/"
                                                        :translations {:fi {:title "Testi"
                                                                            :filename "test-fi.md"}
                                                                       :en {:title "Test"
                                                                            :filename "test-en.md"}
                                                                       :de {:url "https://example.org/de"}}}

                                                       {:id "test-roles"
                                                        :roles [:handler]
                                                        :filename "test-en.md"}]
                                         :extra-pages-path "./test-data/extra-pages/")]
      (testing "fetch existing extra-page"
        (let [response (api-call :get "/api/extra-pages/test" {} api-key user-id)]
          (is (= (set (keys response)) #{:en :fi :de}))
          (testing "fallback"
            (is (= "This is a test.\n" (:en response)) "should have fallback")
            (is (= "Tämä on testi.\n" (:fi response)) "should have custom localization")
            (is (= "This is a test.\n" (:de response)) "should have fallback though url is intended for front-end")))

        (let [response (api-call :get "/api/extra-pages/test2" {} api-key user-id)]
          (is (= (set (keys response)) #{:en :fi :de}))
          (testing "without fallback"
            (is (= nil (:de response)) "should have no content as url is intended for front-end"))))

      (testing "roles"
        (testing "not with the role"
          (let [response (-> (request :get "/api/extra-pages/test-roles")
                             (authenticate api-key user-id)
                             handler)]
            (is (response-is-not-found? response))))

        (testing "with the role"
          (let [response (-> (request :get "/api/extra-pages/test-roles")
                             (authenticate api-key "handler")
                             handler)]
            (is (response-is-not-found? response)))))

      (testing "try to fetch non-existing extra-page"
        (let [response (-> (request :get "/api/extra-pages/test-does-not-exist")
                           (authenticate api-key user-id)
                           handler)]
          (is (response-is-not-found? response)))))))
