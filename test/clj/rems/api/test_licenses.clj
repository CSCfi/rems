(ns ^:integration rems.api.test-licenses
  (:require [clojure.test :refer :all]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :each
  api-fixture)

(def testfile (clojure.java.io/file "./test-data/test.txt"))

(def filecontent {:tempfile testfile
                  :content-type "text/plain"
                  :filename "test.txt"
                  :size (.length testfile)})

(deftest licenses-api-test
  (let [api-key "42"
        user-id "owner"
        org-owner "organization-owner1"
        create-license (fn [user-id command]
                         (-> (request :post "/api/licenses/create")
                             (authenticate api-key user-id)
                             (json-body command)
                             handler
                             assert-response-is-ok
                             read-body))]
    (testing "get all"
      (let [data (-> (request :get "/api/licenses")
                     (authenticate api-key user-id)
                     handler
                     assert-response-is-ok
                     read-body)
            id (:id (first data))]
        (is id)
        (testing "get one"
          (let [data (-> (request :get (str "/api/licenses/" id))
                         (authenticate api-key user-id)
                         handler
                         assert-response-is-ok
                         read-body)]
            (is (= id (:id data)))))))

    (testing "get all as organization owner"
      (let [data (-> (request :get "/api/licenses")
                     (authenticate api-key org-owner)
                     handler
                     assert-response-is-ok
                     read-body)
            id (:id (first data))]
        (is id)
        (is (apply = (map :organization data)))
        (testing "get one as organization owner"
          (let [data (-> (request :get (str "/api/licenses/" id))
                         (authenticate api-key org-owner)
                         handler
                         assert-response-is-ok
                         read-body)]
            (is (= id (:id data)))))))

    (testing "create linked license"
      (let [command {:licensetype "link"
                     :organization "test-organization"
                     :localizations {:en {:title "en title"
                                          :textcontent "http://example.com/license/en"
                                          :attachment-id nil}
                                     :fi {:title "fi title"
                                          :textcontent "http://example.com/license/fi"
                                          :attachment-id nil}}}]
        (testing "as owner"
          (let [id (:id (create-license "owner" command))]
            (is id)
            (testing "and fetch"
              (let [license (-> (request :get (str "/api/licenses/" id))
                                (authenticate api-key user-id)
                                handler
                                assert-response-is-ok
                                read-body)]
                (is license)
                (is (= command (select-keys license (keys command))))))))

        (testing "as organization owner"
          (testing "with correct organization"
            (let [body (create-license "organization-owner1" (assoc command :organization "organization1"))]
              (is (:id body))
              (is (:success body))))

          (testing "with correct organization"
            (let [body (create-license "organization-owner1" (assoc command :organization "organization2"))]
              (is (not (:success body))))))))

    (testing "create inline license"
      (let [command {:licensetype "text"
                     :organization "test-organization"
                     :localizations {:en {:title "en title"
                                          :textcontent "en text"
                                          :attachment-id nil}
                                     :fi {:title "fi title"
                                          :textcontent "fi text"
                                          :attachment-id nil}}}
            body (create-license user-id command)
            id (:id body)]
        (is id)
        (is (:success body))
        (testing "and fetch"
          (let [license (-> (request :get (str "/api/licenses/" id))
                            (authenticate api-key user-id)
                            handler
                            read-ok-body)]
            (is license)
            (is (= command (select-keys license (keys command))))))))

    (testing "Upload an attachment"
      (let [response (-> (request :post (str "/api/licenses/add_attachment"))
                         (assoc :params {"file" filecontent})
                         (assoc :multipart-params {"file" filecontent})
                         (authenticate api-key user-id)
                         handler
                         assert-response-is-ok)
            {:keys [id]} (read-body response)]

        (testing "and test that an id is returned" (is (some? id)))

        (testing "and test that it can be accessed using GET"
          (let [response-file (is (-> (request :get (str "/api/licenses/attachments/" id))
                                      (authenticate api-key user-id)
                                      handler
                                      assert-response-is-ok))]
            (is (= "attachment;filename=\"test.txt\"" (get-in response-file [:headers "Content-Disposition"])))
            (is (= (slurp testfile) (slurp (:body response-file))))))

        (testing "and delete it"
          (-> (request :post (str "/api/licenses/remove_attachment?attachment-id=" id))
              (json-body {:attachment-id id})
              (authenticate api-key user-id)
              handler
              assert-response-is-ok))

        (testing "and check it's not found after deletion"
          (let [response (is (-> (request :get (str "/api/licenses/attachments/" id))
                                 (authenticate api-key user-id)
                                 handler))]
            (is (response-is-not-found? response))))))

    (testing "create attachment license"
      (let [attachment-id (-> (request :post (str "/api/licenses/add_attachment"))
                              (assoc :params {"file" filecontent})
                              (assoc :multipart-params {"file" filecontent})
                              (authenticate api-key user-id)
                              handler
                              read-ok-body
                              :id)
            command {:licensetype "text"
                     :organization "test-organization"
                     :localizations {:en {:title "en title"
                                          :textcontent "en text"
                                          :attachment-id attachment-id}
                                     :fi {:title "fi title"
                                          :textcontent "fi text"
                                          :attachment-id attachment-id}}}
            license-id (-> (request :post "/api/licenses/create")
                           (authenticate api-key user-id)
                           (json-body command)
                           handler
                           read-ok-body
                           :id)]

        (testing "and fetch"
          (let [license (-> (request :get (str "/api/licenses/" license-id))
                            (authenticate api-key user-id)
                            handler
                            read-ok-body)]
            (is license)
            (is (= command (select-keys license (keys command))))))

        (testing "and fail when trying to remove the attachment of the created license"
          (-> (request :post (str "/api/licenses/remove_attachment?attachment-id=" attachment-id))
              (json-body {:attachment-id attachment-id})
              (authenticate api-key user-id)
              handler
              assert-response-is-server-error?))))))

(deftest licenses-api-filtering-test
  (let [unfiltered (-> (request :get "/api/licenses" {:disabled true})
                       (authenticate "42" "owner")
                       handler
                       assert-response-is-ok
                       read-body)
        filtered (-> (request :get "/api/licenses")
                     (authenticate "42" "owner")
                     handler
                     assert-response-is-ok
                     read-body)]
    (is (coll-is-not-empty? unfiltered))
    (is (coll-is-not-empty? filtered))
    (is (< (count filtered) (count unfiltered)))))

(deftest licenses-api-security-test
  (testing "without authentication"
    (testing "list"
      (let [response (-> (request :get "/api/licenses")
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post "/api/licenses/create")
                         (json-body {:licensetype "text"
                                     :organization "test-organization"
                                     :localizations {:en {:title "t"
                                                          :textcontent "t"}}})
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (-> (request :get "/api/licenses")
                         (authenticate "42" "alice")
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post "/api/licenses/create")
                         (authenticate "42" "alice")
                         (json-body {:licensetype "text"
                                     :organization "test-organization"
                                     :localizations {:en {:title "t"
                                                          :textcontent "t"}}})
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))
