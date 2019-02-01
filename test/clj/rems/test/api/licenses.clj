(ns ^:integration rems.test.api.licenses
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [ring.mock.request :refer :all])
  (:import (java.util UUID)))

(use-fixtures
  :once
  api-fixture)

(deftest licenses-api-test
  (let [api-key "42"
        user-id "owner"]
    (testing "get"
      (let [data (-> (request :get "/api/licenses")
                     (authenticate api-key user-id)
                     app
                     assert-response-is-ok
                     read-body)]
        (is (coll-is-not-empty? data))
        (is (= #{:id :start :end :licensetype :title :textcontent :localizations :attachment-id} (set (keys (first data)))))))

    (testing "create linked license"
      (let [command {:title (str "license title " (UUID/randomUUID))
                     :licensetype "link"
                     :textcontent "http://example.com/license"
                     :attachment-id nil
                     :localizations {:en {:title "en title"
                                          :textcontent "http://example.com/license/en"
                                          :attachment-id nil}
                                     :fi {:title "fi title"
                                          :textcontent "http://example.com/license/fi"
                                          :attachment-id nil}}}]
        (-> (request :post "/api/licenses/create")
            (authenticate api-key user-id)
            (json-body command)
            app
            assert-response-is-ok)
        (testing "and fetch"
          (let [body (-> (request :get "/api/licenses")
                         (authenticate api-key user-id)
                         app
                         assert-response-is-ok
                         read-body)
                license (->> body
                             (filter #(= (:title %) (:title command)))
                             first)]
            (is license)
            (is (= command (select-keys license (keys command))))))))

    (testing "create inline license"
      (let [command {:title (str "license title " (UUID/randomUUID))
                     :licensetype "text"
                     :textcontent "license text"
                     :attachment-id nil
                     :localizations {:en {:title "en title"
                                          :textcontent "en text"
                                          :attachment-id nil}
                                     :fi {:title "fi title"
                                          :textcontent "fi text"
                                          :attachment-id nil}}}]
        (-> (request :post "/api/licenses/create")
            (authenticate api-key user-id)
            (json-body command)
            app
            assert-response-is-ok)
        (testing "and fetch"
          (let [body (-> (request :get "/api/licenses")
                         (authenticate api-key user-id)
                         app
                         assert-response-is-ok
                         read-body)
                license (->> body
                             (filter #(= (:title %) (:title command)))
                             first)]
            (is license)
            (is (= command (select-keys license (keys command))))))))))

(deftest licenses-api-filtering-test
  (let [unfiltered (-> (request :get "/api/licenses")
                       (authenticate "42" "owner")
                       app
                       assert-response-is-ok
                       read-body)
        filtered (-> (request :get "/api/licenses" {:active true})
                     (authenticate "42" "owner")
                     app
                     assert-response-is-ok
                     read-body)]
    (is (coll-is-not-empty? unfiltered))
    (is (coll-is-not-empty? filtered))
    (is (< (count filtered) (count unfiltered)))))

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
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (-> (request :get "/api/licenses")
                         (authenticate "42" "alice")
                         app)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post "/api/licenses/create")
                         (authenticate "42" "alice")
                         (json-body {:licensetype "text"
                                     :title "t"
                                     :textcontent "t"
                                     :localizations {:en {:title "t"
                                                          :textcontent "t"}}})
                         app)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))
