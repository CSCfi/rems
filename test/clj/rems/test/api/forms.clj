(ns ^:integration rems.test.api.forms
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [ring.mock.request :refer :all])
  (:import (java.util UUID)))

(use-fixtures
  :once
  api-fixture)

(defn- get-draft-form [form-id]
  ;; XXX: there is no simple API for reading the form items
  (let [api-key "42"
        catalogue-item (-> (request :post "/api/catalogue-items/create")
                           (authenticate api-key "owner")
                           (json-body {:title "tmp"
                                       :form form-id
                                       :resid 1
                                       :wfid 1})
                           app
                           assert-response-is-ok
                           read-body)
        draft (-> (request :get "/api/applications/draft" {:catalogue-items (:id catalogue-item)})
                  (authenticate api-key "alice")
                  app
                  assert-response-is-ok
                  read-body)]
    draft))

(deftest forms-api-test
  (let [api-key "42"
        user-id "owner"]
    (testing "get"
      (let [data (-> (request :get "/api/forms")
                     (authenticate api-key user-id)
                     app
                     assert-response-is-ok
                     read-body)]
        (is (coll-is-not-empty? data))
        (is (= #{:id :organization :title :start :end :active}
               (set (keys (first data)))))))

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
          (let [command-with-invalid-maxlength (assoc-in command [:items 0 :maxlength] -1)
                response (-> (request :post "/api/forms/create")
                             (authenticate api-key user-id)
                             (json-body command-with-invalid-maxlength)
                             app)]
            (is (= 400 (:status response))
                "can't send negative maxlength")))
        (testing "invalid create: field too long"
          (let [command-with-long-prompt (assoc-in command [:items 0 :input-prompt :en]
                                                   (apply str (repeat 10000 "x")))
                response (-> (request :post "/api/forms/create")
                             (authenticate api-key user-id)
                             (json-body command-with-long-prompt)
                             app)]
            (is (= 500 (:status response)))))
        (testing "valid create"
          (-> (request :post "/api/forms/create")
              (authenticate api-key user-id)
              (json-body command)
              app
              assert-response-is-ok))
        (testing "and fetch"
          (let [body (-> (request :get "/api/forms")
                         (authenticate api-key user-id)
                         app
                         assert-response-is-ok
                         read-body)
                forms (->> body
                           (filter #(= (:title %) (:title command))))
                form (first forms)]
            (is (= 1 (count forms))
                "only one form got created")
            ;; TODO: create an API for reading full forms (will be needed latest for editing forms)
            (is (= (select-keys command [:title :organization])
                   (select-keys form [:title :organization])))
            (is (= [{:optional true
                     :type "text"
                     :localizations {:en {:title "en title"
                                          :inputprompt "en prompt"}
                                     :fi {:title "fi title"
                                          :inputprompt "fi prompt"}}}]
                   (->> (get-draft-form (:id form))
                        :items
                        (map #(select-keys % [:optional :type :localizations])))))))))))

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
            app
            assert-response-is-ok)

        (testing "and fetch"
          (let [body (-> (request :get "/api/forms")
                         (authenticate api-key user-id)
                         app
                         assert-response-is-ok
                         read-body)
                form (->> body
                          (filter #(= (:title %) (:title command)))
                          first)]
            (is (= [{:optional true
                     :type "option"
                     :localizations {:en {:title "en title"
                                          :inputprompt nil}
                                     :fi {:title "fi title"
                                          :inputprompt nil}}
                     :options [{:key "yes"
                                :label {:en "Yes"
                                        :fi "Kyllä"}}
                               {:key "no"
                                :label {:en "No"
                                        :fi "Ei"}}]}]
                   (->> (get-draft-form (:id form))
                        :items
                        (map #(select-keys % [:optional :type :localizations :options])))))))))))

(deftest forms-api-filtering-test
  (let [unfiltered (-> (request :get "/api/forms")
                       (authenticate "42" "owner")
                       app
                       assert-response-is-ok
                       read-body)
        filtered (-> (request :get "/api/forms" {:active true})
                     (authenticate "42" "owner")
                     app
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
                         app)
            body (read-body response)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" body))))
    (testing "create"
      (let [response (-> (request :post "/api/forms/create")
                         (json-body {:organization "abc"
                                     :title "the title"
                                     :items []})
                         app)]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (-> (request :get (str "/api/forms"))
                         (authenticate "42" "alice")
                         app)
            body (read-body response)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" body))))
    (testing "create"
      (let [response (-> (request :post "/api/forms/create")
                         (authenticate "42" "alice")
                         (json-body {:organization "abc"
                                     :title "the title"
                                     :items []})
                         app)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))
