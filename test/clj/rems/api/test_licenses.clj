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
        owner "owner"
        org-owner "organization-owner1"]
    (testing "fetch nonexistent"
      (let [resp (api-response :get "/api/licenses/99999999"
                               nil
                               api-key owner)]
        (is (response-is-not-found? resp))
        (is (= {:error "not found"} (read-body resp)))))
    (testing "can't create license as organization owner with incorrect organization"
      (is (response-is-forbidden? (api-response :post "/api/licenses/create"
                                                {:licensetype "link"
                                                 :organization {:organization/id "organization2"}
                                                 :localizations {:en {:title "en title"
                                                                      :textcontent "http://example.com/license/en"}
                                                                 :fi {:title "fi title"
                                                                      :textcontent "http://example.com/license/fi"}}}
                                                api-key "organization-owner1"))))

    (doseq [user [org-owner owner]]
      (testing user
        (testing "get all"
          (let [data (api-call :get "/api/licenses" nil
                               api-key user)
                id (:id (first data))]
            (is id)
            (testing "get one"
              (let [data (api-call :get (str "/api/licenses/" id) nil
                                   api-key user)]
                (is (= id (:id data)))))))

        (testing "create link license"
          (let [command {:licensetype "link"
                         :organization {:organization/id "organization1"}
                         :localizations {:en {:title "en title"
                                              :textcontent "http://example.com/license/en"
                                              :attachment-id nil}
                                         :fi {:title "fi title"
                                              :textcontent "http://example.com/license/fi"
                                              :attachment-id nil}}}
                id (:id (api-call :post "/api/licenses/create" command
                                  api-key user))]
            (is id)
            (testing "and fetch"
              (let [license (api-call :get (str "/api/licenses/" id) nil
                                      api-key user)]
                (is (= {:id id
                        :licensetype "link"
                        :organization {:organization/id "organization1"
                                       :organization/name {:fi "Organization 1" :en "Organization 1" :sv "Organization 1"}
                                       :organization/short-name {:fi "ORG 1" :en "ORG 1" :sv "ORG 1"}}
                        :localizations {:en {:title "en title"
                                             :textcontent "http://example.com/license/en"
                                             :attachment-id nil}
                                        :fi {:title "fi title"
                                             :textcontent "http://example.com/license/fi"
                                             :attachment-id nil}}
                        :enabled true
                        :archived false}
                       license))))))

        (testing "create inline license"
          (let [command {:licensetype "text"
                         :organization {:organization/id "organization1"}
                         :localizations {:en {:title "en title"
                                              :textcontent "en text"
                                              :attachment-id nil}
                                         :fi {:title "fi title"
                                              :textcontent "fi text"
                                              :attachment-id nil}}}
                body (api-call :post "/api/licenses/create" command
                               api-key user)
                id (:id body)]
            (is id)
            (is (:success body))
            (testing "and fetch"
              (let [license (api-call :get (str "/api/licenses/" id) nil
                                      api-key user)]
                (is license)
                (is (= {:id id
                        :licensetype "text"
                        :organization {:organization/id "organization1"
                                       :organization/name {:fi "Organization 1" :en "Organization 1" :sv "Organization 1"}
                                       :organization/short-name {:fi "ORG 1" :en "ORG 1" :sv "ORG 1"}}
                        :localizations {:en {:title "en title"
                                             :textcontent "en text"
                                             :attachment-id nil}
                                        :fi {:title "fi title"
                                             :textcontent "fi text"
                                             :attachment-id nil}}
                        :enabled true
                        :archived false}
                       license))))))

        (testing "Upload an attachment"
          (let [response (-> (request :post (str "/api/licenses/add_attachment"))
                             (assoc :params {"file" filecontent})
                             (assoc :multipart-params {"file" filecontent})
                             (authenticate api-key user)
                             handler
                             assert-response-is-ok)
                {:keys [id]} (read-body response)]

            (testing "and test that an id is returned"
              (is (some? id)))

            (testing "and test that it can be accessed using GET"
              (let [response-file (is (-> (api-response :get (str "/api/licenses/attachments/" id) nil
                                                        api-key user)
                                          assert-response-is-ok))]
                (is (= "attachment;filename=\"test.txt\"" (get-in response-file [:headers "Content-Disposition"])))
                (is (= (slurp testfile) (slurp (:body response-file))))))

            (testing "and delete it"
              (api-call :post (str "/api/licenses/remove_attachment?attachment-id=" id)
                        {:attachment-id id}
                        api-key user))

            (testing "and check it's not found after deletion"
              (is (response-is-not-found? (api-response :get (str "/api/licenses/attachments/" id) nil
                                                        api-key user))))))

        (testing "create attachment license"
          (let [attachment-id (-> (request :post (str "/api/licenses/add_attachment"))
                                  (assoc :params {"file" filecontent})
                                  (assoc :multipart-params {"file" filecontent})
                                  (authenticate api-key user)
                                  handler
                                  read-ok-body
                                  :id)
                command {:licensetype "text"
                         :organization {:organization/id "organization1"}
                         :localizations {:en {:title "en title"
                                              :textcontent "en text"
                                              :attachment-id attachment-id}
                                         :fi {:title "fi title"
                                              :textcontent "fi text"
                                              :attachment-id attachment-id}}}
                license-id (:id (api-call :post "/api/licenses/create" command
                                          api-key user))]

            (testing "and fetch"
              (let [license (api-call :get (str "/api/licenses/" license-id) nil
                                      api-key user)]
                (is license)
                (is (= {:id license-id
                        :licensetype "text"
                        :organization {:organization/id "organization1"
                                       :organization/name {:fi "Organization 1" :en "Organization 1" :sv "Organization 1"}
                                       :organization/short-name {:fi "ORG 1" :en "ORG 1" :sv "ORG 1"}}
                        :localizations {:en {:title "en title"
                                             :textcontent "en text"
                                             :attachment-id attachment-id}
                                        :fi {:title "fi title"
                                             :textcontent "fi text"
                                             :attachment-id attachment-id}}
                        :enabled true
                        :archived false}
                       license))))

            ;; this test case invalidates the transaction, so we only run it very last
            (when (= user owner)
              (testing "and fail when trying to remove the attachment of the created license"
                (is (response-is-server-error?
                     (api-response :post (str "/api/licenses/remove_attachment?attachment-id=" attachment-id)
                                   {:attachment-id attachment-id}
                                   api-key user)))))))))))

(deftest licenses-api-enable-archive-test
  (let [api-key "42"
        id (:id (api-call :post "/api/licenses/create"
                          {:licensetype "text"
                           :organization {:organization/id "organization1"}
                           :localizations {:en {:title "en title"
                                                :textcontent "en text"
                                                :attachment-id nil}
                                           :fi {:title "fi title"
                                                :textcontent "fi text"
                                                :attachment-id nil}}}
                          api-key "owner"))]
    (is (number? id))
    (doseq [user-id ["owner" "organization-owner1"]]
      (testing user-id
        (testing "disable"
          (is (:success (api-call :put "/api/licenses/enabled"
                                  {:id id :enabled false}
                                  api-key user-id)))
          (testing "archive"
            (is (:success (api-call :put "/api/licenses/archived"
                                    {:id id :archived true}
                                    api-key user-id))))
          (testing "fetch"
            (let [res (api-call :get (str "/api/licenses/" id) {} api-key user-id)]
              (is (false? (:enabled res)))
              (is (true? (:archived res)))))
          (testing "unarchive"
            (is (:success (api-call :put "/api/licenses/archived"
                                    {:id id :archived false}
                                    api-key user-id))))
          (testing "enable"
            (is (:success (api-call :put "/api/licenses/enabled"
                                    {:id id :enabled true}
                                    api-key user-id))))
          (testing "fetch again"
            (let [lic (api-call :get (str "/api/licenses/" id) {} api-key user-id)]
              (is (true? (:enabled lic)))
              (is (false? (:archived lic))))))))
    (testing "as owner of different organization"
      (testing "disable"
        (is (response-is-forbidden? (api-response :put "/api/licenses/enabled"
                                                  {:id id :enabled false}
                                                  api-key "organization-owner2"))))
      (testing "archive"
        (is (response-is-forbidden? (api-response :put "/api/licenses/archived"
                                                  {:id id :archived true}
                                                  api-key "organization-owner2")))))))

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
                                     :organization {:organization/id "test-organization"}
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
                                     :organization {:organization/id "test-organization"}
                                     :localizations {:en {:title "t"
                                                          :textcontent "t"}}})
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))
