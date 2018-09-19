(ns ^:integration rems.test.api.licenses
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [rems.util :refer [index-by]]
            [ring.mock.request :refer :all])
  (:import (java.util UUID)))

(use-fixtures
  :once
  api-fixture)

(deftest licenses-api-test
  (let [api-key "42"
        user-id "owner"]
    (testing "get"
      (let [response (-> (request :get "/api/licenses")
                         (authenticate api-key user-id)
                         app)
            data (read-body response)]
        (is (response-is-ok? response))
        (is (coll-is-not-empty? data))
        (is (= #{:id :start :end :licensetype :title :textcontent :localizations} (set (keys (first data)))))))

    (testing "create linked license"
      (let [command {:title (str "license title " (UUID/randomUUID))
                     :licensetype "link"
                     :textcontent "http://example.com/license"
                     :localizations {:en {:title "en title"
                                          :textcontent "http://example.com/license/en"}
                                     :fi {:title "fi title"
                                          :textcontent "http://example.com/license/fi"}}}
            response (-> (request :post "/api/licenses/create")
                         (authenticate api-key user-id)
                         (json-body command)
                         app)]
        (is (response-is-ok? response))
        (testing "and fetch"
          (let [response (-> (request :get "/api/licenses")
                             (authenticate api-key user-id)
                             app)
                license (->> response
                             read-body
                             (filter #(= (:title %) (:title command)))
                             first)]
            (is (response-is-ok? response))
            (is license)
            (is (= command (select-keys license (keys command))))))))

    (testing "create inline license"
      (let [command {:title (str "license title " (UUID/randomUUID))
                     :licensetype "text"
                     :textcontent "license text"
                     :localizations {:en {:title "en title"
                                          :textcontent "en text"}
                                     :fi {:title "fi title"
                                          :textcontent "fi text"}}}
            response (-> (request :post "/api/licenses/create")
                         (authenticate api-key user-id)
                         (json-body command)
                         app)]
        (is (response-is-ok? response))
        (testing "and fetch"
          (let [response (-> (request :get "/api/licenses")
                             (authenticate api-key user-id)
                             app)
                license (->> response
                             read-body
                             (filter #(= (:title %) (:title command)))
                             first)]
            (is (response-is-ok? response))
            (is license)
            (is (= command (select-keys license (keys command))))))))))

(deftest licenses-api-filtering-test
  (let [unfiltered-response (-> (request :get "/api/licenses")
                                (authenticate "42" "owner")
                                app)
        unfiltered-data (read-body unfiltered-response)
        filtered-response (-> (request :get "/api/licenses" {:active true})
                              (authenticate "42" "owner")
                              app)
        filtered-data (read-body filtered-response)]
    (is (response-is-ok? unfiltered-response))
    (is (response-is-ok? filtered-response))
    (is (coll-is-not-empty? unfiltered-data))
    (is (coll-is-not-empty? filtered-data))
    (is (< (count filtered-data) (count unfiltered-data)))))

(deftest licenses-api-security-test
  (testing "without authentication"
    (testing "list"
      (let [response (-> (request :get "/api/licenses")
                         app)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post "/api/licenses/create")
                         (json-body {:licensetype "text"
                                     :title "t"
                                     :textcontent "t"
                                     :localizations {:en {:title "t"
                                                          :textcontent "t"}}})
                         app)]
        (is (response-is-forbidden? response))
        (is (= "<h1>Invalid anti-forgery token</h1>" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (-> (request :get "/api/licenses")
                         (authenticate "42" "alice")
                         app)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post "/api/licenses/create")
                         (authenticate "42" "alice")
                         (json-body {:licensetype "text"
                                     :title "t"
                                     :textcontent "t"
                                     :localizations {:en {:title "t"
                                                          :textcontent "t"}}})
                         app)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))))
