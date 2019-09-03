(ns ^:integration rems.api.test-administration
  "Tests for invariants across administration APIs."
  (:require [clojure.test :refer :all]
            [rems.db.core :as db]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]))

(use-fixtures
  :once
  api-fixture)

(def api-key "42")
(def user-id "owner")


(deftest test-archiving-disabling
  (let [api-key "42"
        user-id "owner"
        license-id (:id (api-call :post "/api/licenses/create" {:licensetype "text" :localizations {}}
                                  api-key user-id))
        resource-id (:id (api-call :post "/api/resources/create" {:resid "test" :organization "abc" :licenses [license-id]}
                                   api-key user-id))
        form-id (:id (api-call :post "/api/forms/create" {:form/organization "abc" :form/title "form update test" :form/fields []}
                               api-key user-id))
        workflow-id (:id (api-call :post "/api/workflows/create"
                                   {:organization "abc" :title "dynamic workflow"
                                    :type :dynamic :handlers [user-id]}
                                   api-key user-id))
        catalogue-id (:id (api-call :post "/api/catalogue-items/create"
                                    {:form form-id
                                     :resid resource-id
                                     :wfid workflow-id
                                     :localizations {}}
                                    api-key user-id))

        resource-archived! #(api-call :put "/api/resources/archived"
                                      {:id resource-id
                                       :archived %}
                                      api-key user-id)

        resource-enabled! #(api-call :put "/api/resources/enabled"
                                      {:id resource-id
                                       :enabled %}
                                      api-key user-id)

        update-catalogue-item! (fn [{:keys [enabled archived]}]
                                 (api-call :put "/api/catalogue-items/update"
                                           {:id catalogue-id
                                            :enabled enabled
                                            :archived archived}
                                           api-key user-id))

        form-archived! #(api-call :put "/api/forms/archived"
                                  {:id form-id
                                   :archived %}
                                  api-key user-id)

        form-enabled! #(api-call :put "/api/forms/enabled"
                                 {:id form-id
                                  :enabled %}
                                 api-key user-id)

        update-license! (fn [{:keys [enabled archived]}]
                          (api-call :put "/api/licenses/update"
                                    {:id license-id
                                     :enabled enabled
                                     :archived archived}
                                    api-key user-id))

        workflow-archived! #(api-call :put "/api/workflows/archived"
                                      {:id workflow-id
                                       :archived %}
                                      api-key user-id)

        workflow-enabled! #(api-call :put "/api/workflows/enabled"
                                     {:id workflow-id
                                      :enabled %}
                                     api-key user-id)]
    (is license-id)
    (is resource-id)
    (is form-id)
    (is workflow-id)
    (is catalogue-id)

    ;; no api for this yet
    (db/create-workflow-license! {:wfid workflow-id :licid license-id})

    (testing "can disable a resource"
      (is (:success (resource-enabled! false)))

      (testing "can't archive resource if it is part of an active catalogue item"
      (let [resp (resource-archived! true)]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/resource-in-use"
                 :catalogue-items [{:id catalogue-id :localizations {}}]}]
               (:errors resp)))))

    (testing "can disable a form"
      (is (:success (form-enabled! false))))

    (testing "can't archive a form that's in use"
      (let [resp (form-archived! true)]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/form-in-use"
                 :catalogue-items [{:id catalogue-id :localizations {}}]}]
               (:errors resp)))))

    (testing "can disable a workflow"
      (is (:success (workflow-enabled! false))))

    (testing "can't archive a workflow that's in use"
      (let [resp (workflow-archived! true)]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/workflow-in-use"
                 :catalogue-items [{:id catalogue-id :localizations {}}]}]
               (:errors resp)))))

    (testing "can disable a license"
      (is (:success (update-license! {:enabled false :archived false}))))

    (testing "can't archive a license that's in use"
      (let [resp (update-license! {:enabled true :archived true})]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/license-in-use"
                 :resources [{:id resource-id :resid "test"}]
                 :workflows [{:id workflow-id :title "dynamic workflow"}]}]
               (:errors resp)))))

    (testing "can archive a catalogue item"
      (is (:success (update-catalogue-item! {:enabled true :archived true}))))

    (testing "can archive a resource that's not in use"
      (is (:success (resource-archived! true))))

    (testing "can archive a form that's not in use"
      (is (:success (form-archived! true))))

    (testing "can archive a workflow that's not in use"
      (is (:success (workflow-archived! true))))

    (testing "can archive a license that's not in use"
      (is (= {:success true} (update-license! {:enabled true :archived true}))))

    (testing "cannot unarchive a resource with an archived license"
      (let [resp (resource-archived! false)]
        (is (false? (:success resp)))
        (is (= "t.administration.errors/license-archived"
               (get-in resp [:errors 0 :type]))))))))
