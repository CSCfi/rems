(ns ^:integration rems.api.test-forms
  (:require [clojure.test :refer :all]
            [medley.core :refer [dissoc-in]]
            [rems.api.schema :as schema]
            [rems.api.testing :refer :all]
            [rems.handler :refer [handler]]
            [rems.db.api-key :as api-key]
            [rems.db.core :as db]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [owners-fixture +test-api-key+]]
            [ring.mock.request :refer :all])
  (:import (java.util UUID)))

(use-fixtures
  :each
  api-fixture
  owners-fixture)

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
        owner "owner"

        uuid (UUID/randomUUID)
        command {:organization {:organization/id "organization1"}
                 :form/internal-name (str "form name " uuid)
                 :form/external-title {:en (str "en form title " uuid)
                                       :fi (str "fi form title " uuid)
                                       :sv (str "sv form title " uuid)}
                 :form/fields [{:field/title {:en "en title"
                                              :fi "fi title"
                                              :sv "sv title"}
                                :field/optional true
                                :field/type :text
                                :field/placeholder {:en "en placeholder"
                                                    :fi "fi placeholder"
                                                    :sv "sv placeholder"}}]}]
    (doseq [user-id [owner org-owner]]
      (testing user-id
        (testing "not found"
          (let [response (api-response :get "/api/forms/0" nil
                                       api-key user-id)]
            (is (= 404 (:status response)))))

        (testing "create"
          (testing "invalid create"
            ;; TODO: silence the logging for this expected error
            (testing "negative max length"
              (let [command-with-invalid-max-length (assoc-in command [:form/fields 0 :field/max-length] -1)]
                (is (response-is-bad-request? (api-response :post "/api/forms/create"
                                                            command-with-invalid-max-length
                                                            api-key user-id)))))
            (testing "duplicate field ids"
              (let [uuid (UUID/randomUUID)
                    command-with-duplicated-field-ids {:organization {:organization/id "organization1"}
                                                       :form/internal-name (str "form name " uuid)
                                                       :form/external-title {:en (str "en form title " uuid)
                                                                             :fi (str "fi form title " uuid)
                                                                             :sv (str "sv form title " uuid)}
                                                       :form/fields [{:field/id "abc"
                                                                      :field/title {:en "en title"
                                                                                    :fi "fi title"
                                                                                    :sv "sv title"}
                                                                      :field/optional true
                                                                      :field/type :text
                                                                      :field/placeholder {:en "en placeholder"
                                                                                          :fi "fi placeholder"
                                                                                          :sv "sv placeholder"}}
                                                                     {:field/id "abc"
                                                                      :field/title {:en "en title"
                                                                                    :fi "fi title"
                                                                                    :sv "sv title"}
                                                                      :field/optional true
                                                                      :field/type :text
                                                                      :field/placeholder {:en "en placeholder"
                                                                                          :fi "fi placeholder"
                                                                                          :sv "sv placeholder"}}]}]
                (is (response-is-bad-request? (api-response :post "/api/forms/create"
                                                            command-with-duplicated-field-ids
                                                            api-key user-id))))))

          (testing "valid create without field id"
            (let [id (:id (api-call :post "/api/forms/create"
                                    command
                                    api-key user-id))]
              (is id)
              (testing "and fetch"
                (let [form-template (api-call :get (str "/api/forms/" id) nil
                                              api-key user-id)]
                  (testing "result matches input"
                    (is (= (-> command
                               (select-keys [:organization :form/internal-name :form/external-title])
                               (assoc-in
                                [:organization :organization/name] {:fi "Organization 1" :en "Organization 1" :sv "Organization 1"})
                               (assoc-in
                                [:organization :organization/short-name] {:fi "ORG 1" :en "ORG 1" :sv "ORG 1"}))
                           (select-keys form-template [:organization :form/internal-name :form/external-title])))
                    (is (= (:form/fields command)
                           (mapv fixup-field-to-match-command (:form/fields form-template)))))))))
          (testing "valid create with given field id"
            (let [command-with-given-field-id (assoc-in command [:form/fields 0 :field/id] "abc")
                  id (:id (api-call :post "/api/forms/create"
                                    command-with-given-field-id
                                    api-key user-id))]
              (is id)
              (testing "and fetch"
                (let [form-template (api-call :get (str "/api/forms/" id) nil
                                              api-key user-id)]
                  (testing "result matches input"
                    (is (= (-> command-with-given-field-id
                               (select-keys [:organization :form/internal-name :form/external-title])
                               (assoc-in [:organization :organization/name] {:fi "Organization 1" :en "Organization 1" :sv "Organization 1"})
                               (assoc-in
                                [:organization :organization/short-name] {:fi "ORG 1" :en "ORG 1" :sv "ORG 1"}))
                           (select-keys form-template [:organization :form/internal-name :form/external-title])))
                    (is (= (mapv #(dissoc % :field/id) (:form/fields command-with-given-field-id))
                           (mapv fixup-field-to-match-command (:form/fields form-template))))
                    (is (= (get-in command-with-given-field-id [:form/fields 0 :field/id]) ; field/id "not" in previous comparison
                           (get-in form-template [:form/fields 0 :field/id]))))))))
          (testing "get all"
            (let [data (api-call :get "/api/forms" nil
                                 api-key user-id)]
              (is (:form/id (first data))))))))
    (testing "create as organization owner with incorrect organization"
      (let [response (api-response :post "/api/forms/create"
                                   (assoc command :organization {:organization/id "organization2"})
                                   api-key "organization-owner1")]
        (is (response-is-forbidden? response))
        (is (= "no access to organization \"organization2\"" (read-body response)))))))

(deftest forms-api-all-field-types-test
  (let [api-key "42"
        user-id "owner"
        localized {:en "en" :fi "fi" :sv "sv"}
        form-spec {:organization {:organization/id "default"}
                   :form/internal-name "all field types test"
                   :form/external-title {:en "en all field types test"
                                         :fi "fi all field types test"
                                         :sv "sv all field types test"}
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
                                 {:field/type :table
                                  :field/title localized
                                  :field/optional false
                                  :field/columns [{:key "col1"
                                                   :label localized}
                                                  {:key "col2"
                                                   :label localized}]}
                                 {:field/type :label
                                  :field/title localized
                                  :field/optional true}
                                 {:field/type :header
                                  :field/title localized
                                  :field/optional true}
                                 {:field/type :email
                                  :field/title localized
                                  :field/optional true}
                                 {:field/type :phone-number
                                  :field/title localized
                                  :field/optional true}
                                 {:field/type :ip-address
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
            (is (= (:form/fields form-spec)
                   (mapv fixup-field-to-match-command (:form/fields form))))))))))

(deftest form-editable-test
  (let [api-key "42"
        user-id "owner"]
    (let [form-id (:id (api-call :post "/api/forms/create"
                                 {:organization {:organization/id "organization1"}
                                  :form/internal-name "form editable test"
                                  :form/external-title {:en "en form editable test"
                                                        :fi "fi form editable test"
                                                        :sv "sv form editable test"}
                                  :form/fields []}
                                 api-key user-id))]
      (testing "New form is editable"
        (testing "as owner"
          (is (:success (api-call :get (str "/api/forms/" form-id "/editable") nil
                                  api-key user-id))))
        (testing "as organization owner"
          (is (:success (api-call :get (str "/api/forms/" form-id "/editable") nil
                                  api-key "organization-owner1")))))
      (let [cat-id (test-helpers/create-catalogue-item! {:form-id form-id
                                                         :archived false
                                                         :organization {:organization/id "organization1"}})]
        (testing "Form is non-editable after in use by a catalogue item"
          (is (= {:success false
                  :errors [{:type "t.administration.errors/in-use-by"
                            :catalogue-items [{:id cat-id :localizations {}}]}]}
                 (api-call :get (str "/api/forms/" form-id "/editable") nil
                           api-key user-id)))
          (testing "even if catalogue item is archived & disabled"
            (api-call :put "/api/catalogue-items/archived" {:id cat-id :archived true}
                      api-key user-id)
            (api-call :put "/api/catalogue-items/enabled" {:id cat-id :enabled false}
                      api-key user-id)
            (is (= {:success false
                    :errors [{:type "t.administration.errors/in-use-by"
                              :catalogue-items [{:id cat-id :localizations {}}]}]}
                   (api-call :get (str "/api/forms/" form-id "/editable") nil
                             api-key user-id)))))))
    (let [form-id (:id (api-call :post "/api/forms/create"
                                 {:organization {:organization/id "organization1"}
                                  :form/internal-name "form editable test 2"
                                  :form/external-title {:en "en form editable test 2"
                                                        :fi "fi form editable test 2"
                                                        :sv "sv form editable test 2"}
                                  :form/fields []}
                                 api-key user-id))]
      (testing "New form is editable"
        (is (:success (api-call :get (str "/api/forms/" form-id "/editable") nil
                                api-key user-id))))
      (testing "Form is non-editable after in use by a workflow"
        (let [wfid (test-helpers/create-workflow! {:forms [{:form/id form-id}]
                                                   :title "wf with form"})]
          (is (= {:success false
                  :errors [{:type "t.administration.errors/in-use-by"
                            :workflows [{:id wfid :title "wf with form"}]}]}
                 (api-call :get (str "/api/forms/" form-id "/editable") nil
                           api-key user-id)))
          (testing "even if catalogue item is archived & disabled"
            (api-call :put "/api/workflows/archived" {:id wfid :archived true}
                      api-key user-id)
            (api-call :put "/api/workflows/enabled" {:id wfid :enabled false}
                      api-key user-id)
            (is (= {:success false
                    :errors [{:type "t.administration.errors/in-use-by"
                              :workflows [{:id wfid :title "wf with form"}]}]}
                   (api-call :get (str "/api/forms/" form-id "/editable") nil
                             api-key user-id)))))))))

(deftest form-edit-test
  (let [api-key "42"
        user-id "owner"]
    (let [form-id (:id (api-call :post "/api/forms/create"
                                 {:organization {:organization/id "organization1"}
                                  :form/internal-name "form edit test"
                                  :form/external-title {:en "en form edit test"
                                                        :fi "fi form edit test"
                                                        :sv "sv form edit test"}
                                  :form/fields []}
                                 api-key user-id))]
      (testing "organization owner"
        (testing "can edit name and title in own organization"
          (is (true? (:success (api-call :put "/api/forms/edit"
                                         {:form/id form-id
                                          :organization {:organization/id "organization1"}
                                          :form/internal-name "changed name"
                                          :form/external-title {:en "en changed title"
                                                                :fi "fi changed title"
                                                                :sv "sv changed title"}
                                          :form/fields []}
                                         api-key "organization-owner1"))))
          (is (= {:form/internal-name "changed name"
                  :form/external-title {:en "en changed title"
                                        :fi "fi changed title"
                                        :sv "sv changed title"}}
                 (select-keys (api-call :get (str "/api/forms/" form-id) {} api-key "organization-owner1")
                              [:form/internal-name :form/external-title]))))
        (testing "can't edit title in another organization"
          (is (response-is-forbidden? (api-response :put "/api/forms/edit"
                                                    {:form/id form-id
                                                     :organization {:organization/id "organization1"}
                                                     :form/internal-name "changed name more"
                                                     :form/external-title {:en "en changed title more"
                                                                           :fi "fi changed title more"
                                                                           :sv "sv changed title more"}
                                                     :form/fields []}
                                                    api-key "organization-owner2"))))
        (testing "can't change organization"
          (is (response-is-forbidden? (api-response :put "/api/forms/edit"
                                                    {:form/id form-id
                                                     :organization {:organization/id "organization2"}
                                                     :form/internal-name "changed name more"
                                                     :form/external-title {:en "en changed title more"
                                                                           :fi "fi changed title more"
                                                                           :sv "sv changed title more"}
                                                     :form/fields []}
                                                    api-key "organization-owner1")))))
      (testing "owner can change title and organization"
        (is (true? (:success (api-call :put "/api/forms/edit"
                                       {:form/id form-id
                                        :organization {:organization/id "organization2"}
                                        :form/internal-name "I am owner"
                                        :form/external-title {:en "en I am owner"
                                                              :fi "fi I am owner"
                                                              :sv "sv I am owner"}
                                        :form/fields []}
                                       api-key user-id))))
        (let [form (api-call :get (str "/api/forms/" form-id) {} api-key user-id)]
          (is (= {:form/internal-name "I am owner"
                  :form/external-title {:en "en I am owner"
                                        :fi "fi I am owner"
                                        :sv "sv I am owner"}}
                 (select-keys form [:form/internal-name :form/external-title])))
          (is (= "organization2"
                 (get-in form [:organization :organization/id]))))))))

(deftest form-enabled-archived-test
  (let [api-key "42"
        owner "owner"
        org-owner "organization-owner1"]
    (let [form-id (-> (request :post "/api/forms/create")
                      (authenticate api-key owner)
                      (json-body {:organization {:organization/id "organization1"}
                                  :form/internal-name "form update test"
                                  :form/external-title {:en "en form update test"
                                                        :fi "fi form update test"
                                                        :sv "sv form update test"}
                                  :form/fields []})
                      handler
                      read-ok-body
                      :id)]
      (is (not (nil? form-id)))
      (doseq [user-id [owner org-owner]]
        (testing user-id
          (testing "disable"
            (is (:success (api-call :put "/api/forms/enabled"
                                    {:id form-id :enabled false}
                                    api-key user-id)))
            (testing "archive"
              (is (:success (api-call :put "/api/forms/archived"
                                      {:id form-id :archived true}
                                      api-key user-id))))
            (testing "fetch"
              (let [form (api-call :get (str "/api/forms/" form-id) {} api-key user-id)]
                (is (false? (:enabled form)))
                (is (true? (:archived form)))))
            (testing "unarchive"
              (is (:success (api-call :put "/api/forms/archived"
                                      {:id form-id :archived false}
                                      api-key user-id))))
            (testing "enable"
              (is (:success (api-call :put "/api/forms/enabled"
                                      {:id form-id :enabled true}
                                      api-key user-id))))
            (testing "fetch again"
              (let [form (api-call :get (str "/api/forms/" form-id) {} api-key user-id)]
                (is (true? (:enabled form)))
                (is (false? (:archived form))))))))
      (testing "as owner of different organization"
        (testing "disable"
          (is (response-is-forbidden? (api-response :put "/api/forms/enabled"
                                                    {:id form-id :enabled false}
                                                    api-key "organization-owner2"))))
        (testing "archive"
          (is (response-is-forbidden? (api-response :put "/api/forms/archived"
                                                    {:id form-id :archived true}
                                                    api-key "organization-owner2"))))))))

(deftest option-form-item-test
  (let [api-key "42"
        user-id "owner"
        uuid (UUID/randomUUID)]
    (testing "create"
      (let [command {:organization {:organization/id "default"}
                     :form/internal-name (str "form name " uuid)
                     :form/external-title {:en (str "en form title " uuid)
                                           :fi (str "fi form title " uuid)
                                           :sv (str "sv form title " uuid)}
                     :form/fields [{:field/title {:en "en title"
                                                  :fi "fi title"
                                                  :sv "sv title"}
                                    :field/optional true
                                    :field/type :option
                                    :field/options [{:key "yes"
                                                     :label {:en "Yes"
                                                             :fi "Kyllä"
                                                             :sv "Ja"}}
                                                    {:key "no"
                                                     :label {:en "No"
                                                             :fi "Ei"
                                                             :sv "Nej"}}]}]}
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

(defn- failure-response? [response]
  (or (response-is-bad-request? response)
      (and (response-is-ok? response)
           (false? (:success (read-body response))))))

(deftest forms-api-privacy-test
  (let [api-key "42"
        user-id "owner"
        localized {:en "en" :fi "fi" :sv "sv"}
        command {:organization {:organization/id "default"}
                 :form/internal-name "form fields with privacy"
                 :form/external-title {:en "en form fields with privacy"
                                       :fi "fi form fields with privacy"
                                       :sv "sv form fields with privacy"}
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
        (is (not (failure-response? (api-response :post "/api/forms/create"
                                                  command
                                                  api-key user-id)))
            "sanity check for failure-response?")
        (is (failure-response? (api-response :post "/api/forms/create"
                                             (assoc-in command [:form/fields 1 :field/privacy] nil)
                                             api-key user-id))
            "invalid value")
        (is (failure-response? (api-response :post "/api/forms/create"
                                             (assoc-in command [:form/fields 1 :field/privacy] :does-not-exist)
                                             api-key user-id))
            "invalid value")
        (is (failure-response? (api-response :post "/api/forms/create"
                                             (assoc-in command [:form/fields 0 :field/privacy] :private)
                                             api-key user-id))
            "privacy not supported"))
      (testing "valid request"
        (let [form-id (:id (api-call :post "/api/forms/create"
                                     command
                                     api-key user-id))]
          (is form-id)
          (testing "and fetching"
            (let [form (api-call :get (str "/api/forms/" form-id) nil
                                 api-key user-id)]
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
        localized {:en "en" :fi "fi" :sv "sv"}
        command {:organization {:organization/id "default"}
                 :form/internal-name "text fields that depend on a field"
                 :form/external-title {:en "en text fields that depend on a field"
                                       :fi "fi text fields that depend on a field"
                                       :sv "sv text fields that depend on a field"}
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
        (is (not (failure-response? (api-response :post "/api/forms/create"
                                                  command
                                                  api-key user-id)))
            "sanity check for failure-response?")
        (is (failure-response? (api-response :post "/api/forms/create"
                                             (dissoc-in command [:form/fields 2 :field/visibility :visibility/type] nil)
                                             api-key user-id))
            "missing field")
        (is (failure-response? (api-response :post "/api/forms/create"
                                             (assoc-in command [:form/fields 2 :field/visibility :visibility/type] :doesnotexist)
                                             api-key user-id))
            "invalid type")
        (is (failure-response? (api-response :post "/api/forms/create"
                                             (dissoc-in command [:form/fields 2 :field/visibility :visibility/field] nil)
                                             api-key user-id))
            "missing field")
        (is (failure-response? (api-response :post "/api/forms/create"
                                             (assoc-in command [:form/fields 2 :field/visibility :visibility/field] {})
                                             api-key user-id))
            "missing value")
        (is (failure-response? (api-response :post "/api/forms/create"
                                             (assoc-in command [:form/fields 2 :field/visibility :visibility/field] {:field/id "doesnotexist"})
                                             api-key user-id))
            "referred field does not exist")
        (is (failure-response? (api-response :post "/api/forms/create"
                                             (dissoc-in command [:form/fields 2 :field/visibility :visibility/values] nil)
                                             api-key user-id))
            "missing value")
        (is (failure-response? (api-response :post "/api/forms/create"
                                             (assoc-in command [:form/fields 2 :field/visibility :visibility/values] "c")
                                             api-key user-id))
            "invalid value type")
        (is (failure-response? (api-response :post "/api/forms/create"
                                             (assoc-in command [:form/fields 2 :field/visibility :visibility/values] ["c" "doesnotexist" "d"])
                                             api-key user-id))
            "referred value does not exist")
        (is (failure-response? (api-response :post "/api/forms/create"
                                             (assoc-in command [:form/fields 2 :field/visibility :visibility/values] ["c" "c"])
                                             api-key user-id))
            "duplicate value"))
      (testing "valid request"
        (let [form-id (:id (api-call :post "/api/forms/create"
                                     command
                                     api-key user-id))]
          (is form-id)
          (testing "and fetching"
            (let [form (api-call :get (str "/api/forms/" form-id) nil
                                 api-key user-id)]
              (is (= (-> command
                         (select-keys [:organization :form/internal-name :form/external-title])
                         (assoc-in [:organization :organization/name] {:fi "Oletusorganisaatio" :en "The Default Organization" :sv "Standardorganisationen"})
                         (assoc-in [:organization :organization/short-name] {:fi "Oletus" :en "Default" :sv "Standard"}))
                     (select-keys form [:organization :form/internal-name :form/external-title])))
              (is (= [{:field/id "fld1"
                       :field/type "option"
                       :field/title localized
                       :field/optional false
                       :field/options [{:key "a" :label localized}
                                       {:key "b" :label localized}
                                       {:key "c" :label localized}]}
                      {:field/id "fld4"
                       :field/type "text"
                       :field/title localized
                       :field/optional false}
                      {:field/id "fld5"
                       :field/type "text"
                       :field/title localized
                       :field/optional false
                       :field/visibility {:visibility/type "only-if"
                                          :visibility/field {:field/id "fld1"}
                                          :visibility/values ["c"]}}
                      {:field/id "fld3"
                       :field/type "multiselect"
                       :field/title localized
                       :field/optional false
                       :field/options [{:key "a" :label localized}
                                       {:key "b" :label localized}
                                       {:key "c" :label localized}
                                       {:key "d" :label localized}]}
                      {:field/id "fld7"
                       :field/type "text"
                       :field/title localized
                       :field/optional false
                       :field/visibility {:visibility/type "only-if"
                                          :visibility/field {:field/id "fld3"}
                                          :visibility/values ["c" "d"]}}]
                     (:form/fields form))))))))))

(deftest forms-api-filtering-test
  (db/set-form-template-archived! {:id (test-helpers/create-form! {:form/internal-name "archived 1"
                                                                   :form/external-title {:en "en archived 1"
                                                                                         :fi "fi archived 1"
                                                                                         :sv "sv archived 1"}
                                                                   :form/keys []})
                                   :archived true})
  (db/set-form-template-archived! {:id (test-helpers/create-form! {:form/internal-name "archived 2"
                                                                   :form/external-title {:en "en archived 2"
                                                                                         :fi "fi archived 2"
                                                                                         :sv "sv archived 2"}
                                                                   :form/keys []})
                                   :archived true})
  (test-helpers/create-form! {:form/internal-name "unarchived 1"
                              :form/external-title {:en "en unarchived 1"
                                                    :fi "fi unarchived 1"
                                                    :sv "sv unarchived 1"}
                              :form/keys []})
  (test-helpers/create-form! {:form/internal-name "unarchived 2"
                              :form/external-title {:en "en unarchived 2"
                                                    :fi "fi unarchived 2"
                                                    :sv "sv unarchived 2"}
                              :form/keys []})

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

(deftest test-form-missing-languages
  (let [id (with-redefs [rems.service.form/validation-error (constantly nil)] ;; disable validation
             (test-helpers/create-form! {:form/internal-name "invalid form"
                                         :form/external-title {:en "Invalid Form EN" :fi ""}
                                         :form/fields [{:field/id "fld1"
                                                        :field/type :text
                                                        :field/optional true
                                                        :field/title {:fi "Title in Finnish"}
                                                        :field/placeholder {:en "Placeholder"}}
                                                       {:field/id "fld2"
                                                        :field/type :option
                                                        :field/optional false
                                                        :field/title {:fi "fi" :sv "sv" :en "en"}
                                                        :field/options [{:key "opt"
                                                                         :label {:sv "Swedish label"}}]}]}))]
    (is (= {:form/id id
            :organization {:organization/id "default"
                           :organization/name {:fi "Oletusorganisaatio" :en "The Default Organization" :sv "Standardorganisationen"}
                           :organization/short-name {:fi "Oletus" :en "Default" :sv "Standard"}}
            :form/title "invalid form"
            :form/internal-name "invalid form"
            :form/external-title {:en "Invalid Form EN"
                                  :fi ""}
            :form/fields [{:field/placeholder {:en "Placeholder"}
                           :field/title {:fi "Title in Finnish"}
                           :field/type "text"
                           :field/id "fld1"
                           :field/optional true}
                          {:field/title {:fi "fi" :en "en" :sv "sv"}
                           :field/type "option"
                           :field/id "fld2"
                           :field/options [{:key "opt" :label {:sv "Swedish label"}}]
                           :field/optional false}]
            :form/errors {:form/fields
                          {:0 {:field/title {:en "t.form.validation/required"
                                             :sv "t.form.validation/required"}
                               :field/placeholder {:fi "t.form.validation/required"
                                                   :sv "t.form.validation/required"}}
                           :1 {:field/options
                               {:0
                                {:label {:en "t.form.validation/required"
                                         :fi "t.form.validation/required"}}}}}}
            :enabled true
            :archived false}
           (api-call :get (str "/api/forms/" id) nil
                     "42" "owner")))))

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
                         (json-body {:organization {:organization/id "organization1"}
                                     :form/internal-name "the name"
                                     :form/external-title {:en "en title"
                                                           :fi "fi title"
                                                           :sv "sv title"}
                                     :form/fields []})
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))
  (test-helpers/create-user! {:userid "alice"})
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
                         (json-body {:organization {:organization/id "organization1"}
                                     :form/internal-name "the name"
                                     :form/external-title {:en "en title"
                                                           :fi "fi title"
                                                           :sv "sv title"}
                                     :form/fields []})
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))
