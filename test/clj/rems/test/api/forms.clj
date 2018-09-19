(ns ^:integration rems.test.api.forms
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [rems.util :refer [index-by]]
            [ring.mock.request :refer :all])
  (:import (java.util UUID)))

(use-fixtures
  :once
  api-fixture)

(defn- get-draft-form [form-id]
  ;; XXX: there is no simple API for reading the form items
  (let [api-key "42"
        user-id "owner"
        catalogue-item-response (-> (request :post "/api/catalogue-items/create")
                                    (authenticate api-key user-id)
                                    (json-body {:title "tmp"
                                                :form form-id
                                                :resid 1
                                                :wfid 1})
                                    app)
        catalogue-item (read-body catalogue-item-response)
        draft-response (-> (request :get "/api/applications/draft" {:catalogue-items (:id catalogue-item)})
                           (authenticate api-key user-id)
                           app)
        draft (read-body draft-response)]
    (assert (response-is-ok? catalogue-item-response))
    (assert (response-is-ok? draft-response))
    draft))

(deftest forms-api-test
  (let [api-key "42"
        user-id "owner"]
    (testing "get"
      (let [response (-> (request :get "/api/forms")
                         (authenticate api-key user-id)
                         app)
            data (read-body response)]
        (is (response-is-ok? response))
        (is (coll-is-not-empty? data))
        (is (= #{:id :prefix :title :start :end :active}
               (set (keys (first data)))))))

    (testing "create"
      (let [command {:prefix "abc"
                     :title (str "form title " (UUID/randomUUID))
                     :items [{:title {:en "en title"
                                      :fi "fi title"}
                              :optional true
                              :type "text"
                              :input-prompt {:en "en prompt"
                                             :fi "fi prompt"}}]}
            response (-> (request :post "/api/forms/create")
                         (authenticate api-key user-id)
                         (json-body command)
                         app)]
        (is (response-is-ok? response))

        (testing "and fetch"
          (let [response (-> (request :get "/api/forms")
                             (authenticate api-key user-id)
                             app)
                form (->> response
                          read-body
                          (filter #(= (:title %) (:title command)))
                          first)]
            (is (response-is-ok? response))
            ;; TODO: create an API for reading full forms (will be needed latest for editing forms)
            (is (= (select-keys command [:title :prefix])
                   (select-keys form [:title :prefix])))
            (is (= [{:optional true
                     :type "text"
                     :localizations {:en {:title "en title"
                                          :inputprompt "en prompt"}
                                     :fi {:title "fi title"
                                          :inputprompt "fi prompt"}}}]
                   (->> (get-draft-form (:id form))
                        :items
                        (map #(select-keys % [:optional :type :localizations])))))))))))

(deftest forms-api-filtering-test
  (let [unfiltered-response (-> (request :get "/api/forms")
                                (authenticate "42" "owner")
                                app)
        unfiltered-data (read-body unfiltered-response)
        filtered-response (-> (request :get "/api/forms" {:active true})
                              (authenticate "42" "owner")
                              app)
        filtered-data (read-body filtered-response)]
    (is (response-is-ok? unfiltered-response))
    (is (response-is-ok? filtered-response))
    (is (coll-is-not-empty? unfiltered-data))
    (is (coll-is-not-empty? filtered-data))
    (is (every? #(contains? % :active) unfiltered-data))
    (is (every? :active filtered-data))
    (is (< (count filtered-data) (count unfiltered-data)))))

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
                         (json-body {:prefix "abc"
                                     :title "the title"
                                     :items []})
                         app)]
        (is (response-is-forbidden? response))
        (is (= "<h1>Invalid anti-forgery token</h1>" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (-> (request :get (str "/api/forms"))
                         (authenticate "42" "alice")
                         app)
            body (read-body response)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" body))))
    (testing "create"
      (let [response (-> (request :post "/api/forms/create")
                         (authenticate "42" "alice")
                         (json-body {:prefix "abc"
                                     :title "the title"
                                     :items []})
                         app)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))))
