(ns ^:integration rems.api.test-forms
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all])
  (:import (java.util UUID)))

(use-fixtures
  :once
  api-fixture)

(defn fixup-field-to-match-command [field]
  (-> field
      (dissoc :field/id)
      ;; XXX: these tests use the JSON API, so keywords are not maintained
      (update :field/type keyword)))

(deftest forms-api-test
  (let [api-key "42"
        user-id "owner"]

    (testing "get all"
      (let [data (-> (request :get "/api/forms")
                     (authenticate api-key user-id)
                     handler
                     assert-response-is-ok
                     read-body)]
        (is (:id (first data)))))

    (testing "create"
      (let [command {:organization "abc"
                     :title (str "form title " (UUID/randomUUID))
                     :fields [{:field/title {:en "en title"
                                             :fi "fi title"}
                               :field/optional true
                               :field/type :text
                               :input-prompt {:en "en prompt"
                                              :fi "fi prompt"}}]}]

        (testing "invalid create"
          ;; TODO: silence the logging for this expected error
          (let [command-with-invalid-maxlength (assoc-in command [:fields 0 :maxlength] -1)
                response (-> (request :post "/api/forms/create")
                             (authenticate api-key user-id)
                             (json-body command-with-invalid-maxlength)
                             handler)]
            (is (= 400 (:status response))
                "can't send negative maxlength")))

        (testing "invalid create: field too long"
          (let [command-with-long-prompt (assoc-in command [:fields 0 :input-prompt :en]
                                                   (apply str (repeat 10000 "x")))
                response (-> (request :post "/api/forms/create")
                             (authenticate api-key user-id)
                             (json-body command-with-long-prompt)
                             handler)]
            (is (= 500 (:status response)))))

        (testing "valid create"
          (let [id (-> (request :post "/api/forms/create")
                       (authenticate api-key user-id)
                       (json-body command)
                       handler
                       read-ok-body
                       :id)]
            (is id)
            (testing "and fetch"
              (let [form-template (-> (request :get (str "/api/forms/" id))
                                      (authenticate api-key user-id)
                                      handler
                                      read-ok-body)]
                (testing "result matches input"
                  (is (= (select-keys command [:title :organization])
                         (select-keys form-template [:title :organization])))
                  (is (= (:fields command)
                         (mapv fixup-field-to-match-command (:fields form-template)))))))))))))

(deftest forms-api-all-field-types-test
  (let [api-key "42"
        user-id "owner"
        ;;"attachment" "date" "description" "label" "multiselect" "option" "text" "texta"
        localized {:en "en" :fi "fi"}
        form-spec {:organization "abc" :title "all field types test"
                   :fields [{:field/type :text
                             :field/title localized
                             :field/optional false}
                            {:field/type :texta
                             :field/title localized
                             :field/optional true
                             :maxlength 300
                             :input-prompt localized}
                            {:field/type :description
                             :field/title localized
                             :field/optional false}
                            {:field/type :option
                             :field/title localized
                             :field/optional true
                             :options [{:key "a" :label localized}
                                       {:key "b" :label localized}
                                       {:key "c" :label localized}]}
                            {:field/type :multiselect
                             :field/title localized
                             :field/optional false
                             :options [{:key "a" :label localized}
                                       {:key "b" :label localized}
                                       {:key "c" :label localized}
                                       {:key "d" :label localized}]}
                            {:field/type :label
                             :field/title localized
                             :field/optional true}
                            {:field/type :date
                             :field/title localized
                             :field/optional true}
                            {:field/type :attachment
                             :field/title localized
                             :field/optional false}]}]
    (testing "creating"
      (let [form-id (-> (request :post "/api/forms/create")
                        (authenticate api-key user-id)
                        (json-body form-spec)
                        handler
                        read-ok-body
                        :id)]
        (is form-id)
        (testing "and fetching"
          (let [form (-> (request :get (str "/api/forms/" form-id))
                         (authenticate api-key user-id)
                         handler
                         read-ok-body)]
            (is (= (select-keys form-spec [:organization :title])
                   (select-keys form [:organization :title])))
            (is (= (:fields form-spec)
                   (mapv fixup-field-to-match-command (:fields form))))))))))

(deftest form-editable-test
  (let [api-key "42"
        user-id "owner"
        form-id (-> (request :post "/api/forms/create")
                    (authenticate api-key user-id)
                    (json-body {:organization "abc" :title "form editable test"
                                :fields []})
                    handler
                    read-ok-body
                    :id)]
    (testing "New form is editable"
      (is (:success (-> (request :get (str "/api/forms/" form-id "/editable"))
                        (authenticate api-key user-id)
                        handler
                        read-ok-body))))
    (let [data (-> (request :post "/api/catalogue-items/create")
                   (authenticate api-key user-id)
                   (json-body {:title "test-item-title"
                               :form form-id
                               :resid 1
                               :wfid 1
                               :archived false})
                   handler
                   read-body)]
      (testing "Form is non-editable after in use by a catalogue item"
        (is (not (:success (-> (request :get (str "/api/forms/" form-id "/editable"))
                               (authenticate api-key user-id)
                               handler
                               read-ok-body))))))))

(deftest form-edit-test
  (let [api-key "42"
        user-id "owner"
        form-id (-> (request :post "/api/forms/create")
                    (authenticate api-key user-id)
                    (json-body {:organization "abc" :title "form edit test"
                                :fields []})
                    handler
                    read-ok-body
                    :id)]
    (testing "form content before editing"
      (let [form (-> (request :get (str "/api/forms/" form-id))
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)]
        (is (= (form :organization) "abc")))
      (let [response (-> (request :put (str "/api/forms/" form-id "/edit"))
                         (authenticate api-key user-id)
                         (json-body {:organization "def" :title "form edit test"
                                     :fields []})
                         handler
                         read-ok-body)]
        (testing "form content after editing"
          (let [form (-> (request :get (str "/api/forms/" form-id))
                         (authenticate api-key user-id)
                         handler
                         read-ok-body)]
            (is (= (form :organization) "def"))))))))

(deftest form-update-test
  (let [api-key "42"
        user-id "owner"
        form-id (-> (request :post "/api/forms/create")
                    (authenticate api-key user-id)
                    (json-body {:organization "abc" :title "form update test"
                                :fields []})
                    handler
                    read-ok-body
                    :id)]
    (is (not (nil? form-id)))
    (testing "update"
      (is (:success (-> (request :put "/api/forms/update")
                        (authenticate api-key user-id)
                        (json-body {:id form-id
                                    :enabled false
                                    :archived true})
                        handler
                        read-ok-body))))
    (testing "fetch"
      (let [form (-> (request :get (str "/api/forms/" form-id))
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)]
        (is (false? (:enabled form)))
        (is (true? (:archived form)))))
    (testing "update again"
      (is (:success (-> (request :put "/api/forms/update")
                        (authenticate api-key user-id)
                        (json-body {:id form-id
                                    :enabled true
                                    :archived false})
                        handler
                        read-ok-body))))
    (testing "fetch again"
      (let [form (-> (request :get (str "/api/forms/" form-id))
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)]
        (is (true? (:enabled form)))
        (is (false? (:archived form)))))))

(deftest option-form-item-test
  (let [api-key "42"
        user-id "owner"]
    (testing "create"
      (let [command {:organization "abc"
                     :title (str "form title " (UUID/randomUUID))
                     :fields [{:field/title {:en "en title"
                                             :fi "fi title"}
                               :field/optional true
                               :field/type :option
                               :options [{:key "yes"
                                          :label {:en "Yes"
                                                  :fi "Kyllä"}}
                                         {:key "no"
                                          :label {:en "No"
                                                  :fi "Ei"}}]}]}
            id (-> (request :post "/api/forms/create")
                   (authenticate api-key user-id)
                   (json-body command)
                   handler
                   read-ok-body
                   :id)]

        (testing "and fetch"
          (let [form (-> (request :get (str "/api/forms/" id))
                         (authenticate api-key user-id)
                         handler
                         read-ok-body)]
            (is (= (:fields command)
                   (mapv fixup-field-to-match-command (:fields form))))))))))

(deftest forms-api-filtering-test
  (let [unfiltered (-> (request :get "/api/forms" {:archived true})
                       (authenticate "42" "owner")
                       handler
                       assert-response-is-ok
                       read-body)
        filtered (-> (request :get "/api/forms")
                     (authenticate "42" "owner")
                     handler
                     assert-response-is-ok
                     read-body)]
    (is (coll-is-not-empty? unfiltered))
    (is (coll-is-not-empty? filtered))
    (is (every? #(contains? % :archived) unfiltered))
    (is (not-any? :archived filtered))
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
                                     :fields []})
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
                                     :fields []})
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))
