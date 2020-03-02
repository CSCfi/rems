(ns ^:integration rems.api.test-forms
  (:require [clojure.test :refer :all]
            [medley.core :refer [dissoc-in]]
            [rems.api.schema :as schema]
            [rems.api.testing :refer :all]
            [rems.handler :refer [handler]]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]
            [ring.mock.request :refer :all])
  (:import (java.util UUID)))

(use-fixtures
  :once
  api-fixture)

(defn- fixup-field-visible-type [field]
  (if (get-in field [:field/visibility :visibility/type])
    (update-in field [:field/visibility :visibility/type] keyword)
    field))

(defn- fixup-field-to-match-command [field]
  (-> field
      (dissoc :field/id)
      ;; XXX: these tests use the JSON API, so keywords are not maintained
      (update :field/type keyword)
      fixup-field-visible-type))

(deftest forms-api-test
  (let [api-key "42"
        org-owner "organization-owner1"
        user-id "owner"]

    (testing "get all"
      (let [data (-> (request :get "/api/forms")
                     (authenticate api-key user-id)
                     handler
                     assert-response-is-ok
                     read-body)]
        (is (:form/id (first data)))))

    (testing "get one"
      (let [id (:id (first (db/get-form-templates {})))
            data (-> (request :get (str "/api/forms/" id))
                     (authenticate api-key user-id)
                     handler
                     assert-response-is-ok
                     read-body)]
        (is (:form/id data))))

    (testing "get all as organization owner"
      (let [data (-> (request :get "/api/forms")
                     (authenticate api-key org-owner)
                     handler
                     assert-response-is-ok
                     read-body)
            id (:form/id (first data))]
        (is id)
        (is (apply = (map :form/organization data)))
        (testing "get one as organization owner"
          (let [data (-> (request :get (str "/api/forms/" id))
                     (authenticate api-key org-owner)
                     handler
                     assert-response-is-ok
                     read-body)]
            (is (= id (:form/id data)))))))

    (testing "not found"
      (let [response (-> (request :get "/api/forms/0")
                         (authenticate api-key user-id)
                         handler)]
        (is (= 404 (:status response)))))

    (testing "create"
      (let [command {:form/organization "test-organization"
                     :form/title (str "form title " (UUID/randomUUID))
                     :form/fields [{:field/title {:en "en title"
                                                  :fi "fi title"}
                                    :field/optional true
                                    :field/type :text
                                    :field/placeholder {:en "en placeholder"
                                                        :fi "fi placeholder"}}]}
            create-form (fn [user-id command]
                          (-> (request :post "/api/forms/create")
                              (authenticate api-key user-id)
                              (json-body command)
                              handler))]

        (testing "invalid create"
          ;; TODO: silence the logging for this expected error
          (testing "negative max length"
            (let [command-with-invalid-max-length (assoc-in command [:form/fields 0 :field/max-length] -1)
                  response (create-form "owner" command-with-invalid-max-length)]
              (is (= 400 (:status response)))))
          (testing "duplicate field ids"
            (let [command-with-duplicated-field-ids {:form/organization "abc"
                                                     :form/title (str "form title " (UUID/randomUUID))
                                                     :form/fields [{:field/id "abc"
                                                                    :field/title {:en "en title"
                                                                                  :fi "fi title"}
                                                                    :field/optional true
                                                                    :field/type :text
                                                                    :field/placeholder {:en "en placeholder"
                                                                                        :fi "fi placeholder"}}
                                                                   {:field/id "abc"
                                                                    :field/title {:en "en title"
                                                                                  :fi "fi title"}
                                                                    :field/optional true
                                                                    :field/type :text
                                                                    :field/placeholder {:en "en placeholder"
                                                                                        :fi "fi placeholder"}}]}
                  response (-> (request :post "/api/forms/create")
                               (authenticate api-key user-id)
                               (json-body command-with-duplicated-field-ids)
                               handler)]
              (is (= 400 (:status response))))))

        (testing "valid create without field id"
          (let [id (-> (create-form "owner" command)
                       read-ok-body
                       :id)]
            (is id)
            (testing "and fetch"
              (let [form-template (-> (request :get (str "/api/forms/" id))
                                      (authenticate api-key user-id)
                                      handler
                                      read-ok-body)]
                (testing "result matches input"
                  (is (= (select-keys command [:form/organization :form/title])
                         (select-keys form-template [:form/organization :form/title])))
                  (is (= (:form/fields command)
                         (mapv fixup-field-to-match-command (:form/fields form-template)))))))))
        (testing "valid create with given field id"
          (let [command-with-given-field-id (assoc-in command [:form/fields 0 :field/id] "abc")
                id (-> (create-form "owner" command-with-given-field-id)
                       read-ok-body
                       :id)]
            (is id)
            (testing "and fetch"
              (let [form-template (-> (request :get (str "/api/forms/" id))
                                      (authenticate api-key user-id)
                                      handler
                                      read-ok-body)]
                (testing "result matches input"
                  (is (= (select-keys command-with-given-field-id [:form/organization :form/title])
                         (select-keys form-template [:form/organization :form/title])))
                  (is (= (mapv #(dissoc % :field/id) (:form/fields command-with-given-field-id))
                         (mapv fixup-field-to-match-command (:form/fields form-template))))
                  (is (= (get-in command-with-given-field-id [:form/fields 0 :field/id]) ; field/id "not" in previous comparison
                         (get-in form-template [:form/fields 0 :field/id]))))))))
        (testing "create as organization owner"
          (testing "with correct organization"
            (let [body (-> (create-form "organization-owner1" (assoc command :form/organization "organization1"))
                           read-ok-body)]
              (is (:id body))))

          (testing "with incorrect organization"
            (let [body (-> (create-form "organization-owner1" (assoc command :form/organization "organization2"))
                           read-ok-body)]
              (is (not (:success body))))))))))

(deftest forms-api-all-field-types-test
  (let [api-key "42"
        user-id "owner"
        localized {:en "en" :fi "fi"}
        form-spec {:form/organization "test-organization"
                   :form/title "all field types test"
                   :form/fields [{:field/type :text
                                  :field/title localized
                                  :field/optional false}
                                 {:field/type :texta
                                  :field/title localized
                                  :field/optional true
                                  :field/max-length 300
                                  :field/placeholder localized}
                                 {:field/type :description
                                  :field/title localized
                                  :field/optional false}
                                 {:field/type :option
                                  :field/title localized
                                  :field/optional true
                                  :field/options [{:key "a" :label localized}
                                                  {:key "b" :label localized}
                                                  {:key "c" :label localized}]}
                                 {:field/type :multiselect
                                  :field/title localized
                                  :field/optional false
                                  :field/options [{:key "a" :label localized}
                                                  {:key "b" :label localized}
                                                  {:key "c" :label localized}
                                                  {:key "d" :label localized}]}
                                 {:field/type :label
                                  :field/title localized
                                  :field/optional true}
                                 {:field/type :header
                                  :field/title localized
                                  :field/optional true}
                                 {:field/type :email
                                  :field/title localized
                                  :field/optional true}
                                 {:field/type :date
                                  :field/title localized
                                  :field/optional true}
                                 {:field/type :attachment
                                  :field/title localized
                                  :field/optional false}]}]
    (is (= (:vs (:field/type schema/FieldTemplate))
           (set (map :field/type (:form/fields form-spec))))
        "a new field has been added to schema but not to this test")

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
            (is (= (select-keys form-spec [:form/organization :form/title])
                   (select-keys form [:form/organization :form/title])))
            (is (= (:form/fields form-spec)
                   (mapv fixup-field-to-match-command (:form/fields form))))))))))

(deftest form-editable-test
  (let [api-key "42"
        user-id "owner"
        form-id (-> (request :post "/api/forms/create")
                    (authenticate api-key user-id)
                    (json-body {:form/organization "test-organization"
                                :form/title "form editable test"
                                :form/fields []})
                    handler
                    read-ok-body
                    :id)]
    (testing "New form is editable"
      (is (:success (-> (request :get (str "/api/forms/" form-id "/editable"))
                        (authenticate api-key user-id)
                        handler
                        read-ok-body))))
    (let [resid (test-data/create-resource! {:organization "test-organization"})
          wfid (test-data/create-workflow! {:organization "test-organization"})
          data (-> (request :post "/api/catalogue-items/create")
                   (authenticate api-key user-id)
                   (json-body {:form form-id
                               :resid resid
                               :wfid wfid
                               :archived false
                               :organization "test-organization"
                               :localizations {}})
                   handler
                   read-body)]
      (is (:success data))
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
                    (json-body {:form/organization "test-organization"
                                :form/title "form edit test"
                                :form/fields []})
                    handler
                    read-ok-body
                    :id)]
    (testing "form content before editing"
      (let [form (-> (request :get (str "/api/forms/" form-id))
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)]
        (is (= (:form/organization form) "test-organization")))
      (let [response (-> (request :put "/api/forms/edit")
                         (authenticate api-key user-id)
                         (json-body {:form/id form-id
                                     :form/organization "def"
                                     :form/title "form edit test"
                                     :form/fields []})
                         handler
                         read-ok-body)]
        (testing "form content after editing"
          (let [form (-> (request :get (str "/api/forms/" form-id))
                         (authenticate api-key user-id)
                         handler
                         read-ok-body)]
            (is (= (:form/organization form) "def"))))))))

(deftest form-enabled-archived-test
  (let [api-key "42"
        user-id "owner"
        form-id (-> (request :post "/api/forms/create")
                    (authenticate api-key user-id)
                    (json-body {:form/organization "test-organization"
                                :form/title "form update test"
                                :form/fields []})
                    handler
                    read-ok-body
                    :id)]
    (is (not (nil? form-id)))
    (testing "disable"
      (is (:success (-> (request :put "/api/forms/enabled")
                        (authenticate api-key user-id)
                        (json-body {:id form-id
                                    :enabled false})
                        handler
                        read-ok-body))))
    (testing "archive"
      (is (:success (-> (request :put "/api/forms/archived")
                        (authenticate api-key user-id)
                        (json-body {:id form-id
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
    (testing "unarchive"
      (is (:success (-> (request :put "/api/forms/archived")
                        (authenticate api-key user-id)
                        (json-body {:id form-id
                                    :archived false})
                        handler
                        read-ok-body))))
    (testing "enable"
      (is (:success (-> (request :put "/api/forms/enabled")
                        (authenticate api-key user-id)
                        (json-body {:id form-id
                                    :enabled true})
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
      (let [command {:form/organization "test-organization"
                     :form/title (str "form title " (UUID/randomUUID))
                     :form/fields [{:field/title {:en "en title"
                                                  :fi "fi title"}
                                    :field/optional true
                                    :field/type :option
                                    :field/options [{:key "yes"
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
            (is (= (:form/fields command)
                   (mapv fixup-field-to-match-command (:form/fields form))))))))))

(deftest forms-api-privacy-test
  (let [api-key "42"
        user-id "owner"
        localized {:en "en" :fi "fi"}
        command {:form/organization "abc"
                 :form/title "form fields with privacy"
                 :form/fields [{:field/id "header"
                                :field/type :header
                                :field/title localized
                                :field/optional false}
                               {:field/id "private"
                                :field/type :text
                                :field/title localized
                                :field/optional false
                                :field/privacy :private}
                               {:field/id "public"
                                :field/type :text
                                :field/title localized
                                :field/optional false
                                :field/privacy :public}]}]
    (testing "creating"
      (testing "invalid request"
        (letfn [(fail-request [command]
                  (let [response (-> (request :post "/api/forms/create")
                                     (authenticate api-key user-id)
                                     (json-body command)
                                     handler)]
                    (or (= 400 (:status response))
                        (not (get-in response [:body :success])))))]
          (is (fail-request (assoc-in command [:form/fields 1 :field/privacy] nil)) "invalid value")
          (is (fail-request (assoc-in command [:form/fields 1 :field/privacy] :does-not-exist)) "invalid value")
          (is (fail-request (assoc-in command [:form/fields 0 :field/privacy] :private)) "privacy not supported")))
      (testing "valid request"
        (let [form-id (-> (request :post "/api/forms/create")
                          (authenticate api-key user-id)
                          (json-body command)
                          handler
                          read-ok-body
                          :id)]
          (is form-id)
          (testing "and fetching"
            (let [form (-> (request :get (str "/api/forms/" form-id))
                           (authenticate api-key user-id)
                           handler
                           read-ok-body)]
              (is (= [{:field/id "header"
                       :field/type "header"
                       :field/title localized
                       :field/optional false}
                      {:field/id "private"
                       :field/type "text"
                       :field/title localized
                       :field/optional false
                       :field/privacy "private"}
                      {:field/id "public"
                       :field/type "text"
                       :field/title localized
                       :field/optional false}]
                     (:form/fields form))))))))))

(deftest forms-api-visible-test
  (let [api-key "42"
        user-id "owner"
        localized {:en "en" :fi "fi"}
        command {:form/organization "abc"
                 :form/title "text fields that depend on a field"
                 :form/fields [{:field/id "fld1"
                                :field/type :option
                                :field/title localized
                                :field/optional false
                                :field/options [{:key "a" :label localized}
                                                {:key "b" :label localized}
                                                {:key "c" :label localized}]}
                               {:field/type :text
                                :field/title localized
                                :field/optional false
                                :field/visibility {:visibility/type :always}}
                               {:field/type :text
                                :field/title localized
                                :field/optional false
                                :field/visibility {:visibility/type :only-if
                                                   :visibility/field {:field/id "fld1"}
                                                   :visibility/values ["c"]}}
                               {:field/id "fld3"
                                :field/type :multiselect
                                :field/title localized
                                :field/optional false
                                :field/options [{:key "a" :label localized}
                                                {:key "b" :label localized}
                                                {:key "c" :label localized}
                                                {:key "d" :label localized}]}
                               {:field/type :text
                                :field/title localized
                                :field/optional false
                                :field/visibility {:visibility/type :only-if
                                                   :visibility/field {:field/id "fld3"}
                                                   :visibility/values ["c" "d"]}}]}]
    (testing "creating"
      (testing "invalid request"
        (letfn [(fail-request [command]
                  (let [response (-> (request :post "/api/forms/create")
                                     (authenticate api-key user-id)
                                     (json-body command)
                                     handler)]
                    (or (= 400 (:status response))
                        (not (get-in response [:body :success])))))]
          (is (fail-request (dissoc-in command [:form/fields 2 :field/visibility :visibility/type] nil)) "missing field")
          (is (fail-request (assoc-in command [:form/fields 2 :field/visibility :visibility/type] :doesnotexist)) "invalid type")
          (is (fail-request (dissoc-in command [:form/fields 2 :field/visibility :visibility/field] nil)) "missing field")
          (is (fail-request (assoc-in command [:form/fields 2 :field/visibility :visibility/field] {})) "missing value")
          (is (fail-request (assoc-in command [:form/fields 2 :field/visibility :visibility/field] {:field/id "doesnotexist"})) "referred field does not exist")
          (is (fail-request (dissoc-in command [:form/fields 2 :field/visibility :visibility/values] nil)) "missing value")
          (is (fail-request (assoc-in command [:form/fields 2 :field/visibility :visibility/values] "c")) "invalid value type")
          (is (fail-request (assoc-in command [:form/fields 2 :field/visibility :visibility/values] ["c" "doesnotexist" "d"])) "referred value does not exist")
          (is (fail-request (assoc-in command [:form/fields 2 :field/visibility :visibility/values] ["c" "c"])) "duplicate value")))
      (testing "valid request"
        (let [form-id (-> (request :post "/api/forms/create")
                          (authenticate api-key user-id)
                          (json-body command)
                          handler
                          read-ok-body
                          :id)]
          (is form-id)
          (testing "and fetching"
            (let [form (-> (request :get (str "/api/forms/" form-id))
                           (authenticate api-key user-id)
                           handler
                           read-ok-body)]
              (is (= (select-keys command [:form/organization :form/title])
                     (select-keys form [:form/organization :form/title])))
              (is (= [{:field/id "fld1"
                       :field/type "option"
                       :field/title {:fi "fi" :en "en"}
                       :field/optional false
                       :field/options [{:key "a" :label {:fi "fi" :en "en"}}
                                       {:key "b" :label {:fi "fi" :en "en"}}
                                       {:key "c" :label {:fi "fi" :en "en"}}]}
                      {:field/id "fld4"
                       :field/type "text"
                       :field/title {:fi "fi" :en "en"}
                       :field/optional false}
                      {:field/id "fld5"
                       :field/type "text"
                       :field/title {:fi "fi" :en "en"}
                       :field/optional false
                       :field/visibility {:visibility/type "only-if"
                                          :visibility/field {:field/id "fld1"}
                                          :visibility/values ["c"]}}
                      {:field/id "fld3"
                       :field/type "multiselect"
                       :field/title {:fi "fi" :en "en"}
                       :field/optional false
                       :field/options [{:key "a" :label {:fi "fi" :en "en"}}
                                       {:key "b" :label {:fi "fi" :en "en"}}
                                       {:key "c" :label {:fi "fi" :en "en"}}
                                       {:key "d" :label {:fi "fi" :en "en"}}]}
                      {:field/id "fld7"
                       :field/type "text"
                       :field/title {:fi "fi" :en "en"}
                       :field/optional false
                       :field/visibility {:visibility/type "only-if"
                                          :visibility/field {:field/id "fld3"}
                                          :visibility/values ["c" "d"]}}]
                     (:form/fields form))))))))))

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
                         (json-body {:form/organization "test-organization"
                                     :form/title "the title"
                                     :form/fields []})
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
                         (json-body {:form/organization "test-organization"
                                     :form/title "the title"
                                     :form/fields []})
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))
