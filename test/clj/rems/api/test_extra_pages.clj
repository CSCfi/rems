(ns ^:integration rems.api.test-extra-pages
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture-without-data)

(deftest extra-pages-api-test
  (let [api-key "42"
        user-id "owner"]
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :extra-pages [{:id "test"
                                                        :translations {:fi {:title "Testi"
                                                                            :filename "test-fi.md"}
                                                                       :en {:title "Test"
                                                                            :filename "test-en.md"}}}]
                                         :extra-pages-path "./test-data/extra-pages/")]
      (testing "fetch existing extra-page"
        (let [response (api-call :get "/api/extra-pages/test" {} api-key user-id)]
          (is (= (set (keys response)) #{:en :fi}))
          (is (= (:en response) "This is a test.\n"))))
      (testing "try to fetch non-existing extra-page"
        (let [response (-> (request :get "/api/extra-pages/test2")
                           (authenticate api-key user-id)
                           handler)]
          (is (response-is-not-found? response)))))))
