(ns ^:integration rems.api.test-extra-pages
  (:require [clojure.test :refer :all]
            [rems.service.test-data :as test-data]
            [rems.api.testing :refer :all]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures :once api-fixture)

(deftest extra-pages-api-test
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)
  (with-redefs [rems.config/env (assoc rems.config/env
                                       :extra-pages [{:id "test"
                                                      :filename "test-en.md"
                                                      :translations {:fi {:title "Testi"
                                                                          :filename "test-fi.md"}
                                                                     :en {:title "Test"}
                                                                     :sv {:url "https://example.org/sv"}}}

                                                     {:id "test2"
                                                      :url "https://example.org/"
                                                      :translations {:fi {:title "Testi"
                                                                          :filename "test-fi.md"}
                                                                     :en {:title "Test"
                                                                          :filename "test-en.md"}
                                                                     :sv {:url "https://example.org/sv"}}}

                                                     {:id "test-roles"
                                                      :roles [:owner]
                                                      :filename "test-en.md"}]
                                       :extra-pages-path "./test-data/extra-pages/")]
    (testing "fetch existing extra-page"
      (let [response (api-call :get "/api/extra-pages/test" {} "42" "alice")]
        (is (= (set (keys response)) #{:en :fi :sv}) "should return content in defined languages")
        (is (= "This is a test.\n" (:en response)) "should have fallback")
        (is (= "Tämä on testi.\n" (:fi response)) "should have custom localization")
        (is (= "This is a test.\n" (:sv response)) "should have fallback though url is intended for front-end"))

      (let [response (api-call :get "/api/extra-pages/test2" {} "42" "alice")]
        (is (= (set (keys response)) #{:en :fi :sv}))
        (testing "without fallback"
          (is (nil? (:sv response)) "should have no content as url is intended for front-end"))))

    (testing "roles"
      (testing "without the role"
        (let [response (-> (request :get "/api/extra-pages/test-roles")
                           (authenticate "42" "alice")
                           handler)]
          (is (response-is-not-found? response))))

      (testing "with the role"
        (let [response (-> (request :get "/api/extra-pages/test-roles")
                           (authenticate "42" "owner")
                           handler
                           read-ok-body)]
          (is (= "This is a test.\n" (:en response))))))

    (testing "try to fetch non-existing extra-page"
      (let [response (-> (request :get "/api/extra-pages/test-does-not-exist")
                         (authenticate "42" "alice")
                         handler)]
        (is (response-is-not-found? response))))))
