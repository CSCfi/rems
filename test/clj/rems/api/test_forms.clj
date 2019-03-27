(ns ^:integration rems.api.test-forms
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.form :as form]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all])
  (:import (java.util UUID)))

(use-fixtures :once api-once-fixture)
(use-fixtures :each api-each-fixture)

(deftest forms-api-test
  (let [api-key "42"
        user-id "owner"]

    (testing "get"
      (let [data (-> (request :get "/api/forms")
                     (authenticate api-key user-id)
                     handler
                     assert-response-is-ok
                     read-body)]
        (is (:id (first data)))))

    (testing "create"
      (let [command {:organization "abc"
                     :title (str "form title " (UUID/randomUUID))
                     :items [{:title {:en "en title"
                                      :fi "fi title"}
                              :optional true
                              :type "text"
                              :input-prompt {:en "en prompt"
                                             :fi "fi prompt"}}]}]

        (testing "invalid create"
          ;; TODO: silence the logging for this expected error
          (let [command-with-invalid-maxlength (assoc-in command [:items 0 :maxlength] -1)
                response (-> (request :post "/api/forms/create")
                             (authenticate api-key user-id)
                             (json-body command-with-invalid-maxlength)
                             handler)]
            (is (= 400 (:status response))
                "can't send negative maxlength")))

        (testing "invalid create: field too long"
          (let [command-with-long-prompt (assoc-in command [:items 0 :input-prompt :en]
                                                   (apply str (repeat 10000 "x")))
                response (-> (request :post "/api/forms/create")
                             (authenticate api-key user-id)
                             (json-body command-with-long-prompt)
                             handler)]
            (is (= 500 (:status response)))))

        (testing "valid create"
          (-> (request :post "/api/forms/create")
              (authenticate api-key user-id)
              (json-body command)
              handler
              assert-response-is-ok))

        (testing "and fetch"
          (let [body (-> (request :get "/api/forms")
                         (authenticate api-key user-id)
                         handler
                         assert-response-is-ok
                         read-body)
                forms (->> body
                           (filter #(= (:title %) (:title command))))
                form (first forms)
                form-template (-> (request :get (str "/api/forms/v2/" (:id form)))
                                  (authenticate api-key user-id)
                                  handler
                                  assert-response-is-ok
                                  read-body)]
            (is (= 1 (count forms))
                "only one form got created")

            (testing "form template matches command"
              (is (= (select-keys command [:title :organization])
                     (select-keys form-template [:title :organization])))
              (is (= (:items command)
                     (:fields form-template))))
            (is (= (select-keys command [:title :organization])
                   (select-keys form [:title :organization])))
            (is (= (:items command)
                   (:fields (form/get-form-template (:id form)))))))))))

(deftest option-form-item-test
  (let [api-key "42"
        user-id "owner"]
    (testing "create"
      (let [command {:organization "abc"
                     :title (str "form title " (UUID/randomUUID))
                     :items [{:title {:en "en title"
                                      :fi "fi title"}
                              :optional true
                              :type "option"
                              :options [{:key "yes"
                                         :label {:en "Yes"
                                                 :fi "Kyllä"}}
                                        {:key "no"
                                         :label {:en "No"
                                                 :fi "Ei"}}]}]}]
        (-> (request :post "/api/forms/create")
            (authenticate api-key user-id)
            (json-body command)
            handler
            assert-response-is-ok)

        (testing "and fetch"
          (let [body (-> (request :get "/api/forms")
                         (authenticate api-key user-id)
                         handler
                         assert-response-is-ok
                         read-body)
                form (->> body
                          (filter #(= (:title %) (:title command)))
                          first)]
            (is (= (:items command)
                   (:fields (form/get-form-template (:id form)))))))))))

(deftest forms-api-filtering-test
  (let [unfiltered (-> (request :get "/api/forms")
                       (authenticate "42" "owner")
                       handler
                       assert-response-is-ok
                       read-body)
        filtered (-> (request :get "/api/forms" {:active true})
                     (authenticate "42" "owner")
                     handler
                     assert-response-is-ok
                     read-body)]
    (is (coll-is-not-empty? unfiltered))
    (is (coll-is-not-empty? filtered))
    (is (every? #(contains? % :active) unfiltered))
    (is (every? :active filtered))
    (is (< (count filtered) (count unfiltered)))))

(deftest forms-api-security-test
  (testing "without authentication"
    (testing "list"
      (let [response (-> (request :get (str "/api/forms"))
                         handler)
            body (read-body response)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" body))))
    (testing "create"
      (let [response (-> (request :post "/api/forms/create")
                         (json-body {:organization "abc"
                                     :title "the title"
                                     :items []})
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (-> (request :get (str "/api/forms"))
                         (authenticate "42" "alice")
                         handler)
            body (read-body response)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" body))))
    (testing "create"
      (let [response (-> (request :post "/api/forms/create")
                         (authenticate "42" "alice")
                         (json-body {:organization "abc"
                                     :title "the title"
                                     :items []})
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))
