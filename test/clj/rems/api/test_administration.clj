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
        license-id (:id (api-call :post "/api/licenses/create" {:licensetype "text" :title "x" :textcontent "x" :localizations {}}
                                  api-key user-id))
        resource-id (:id (api-call :post "/api/resources/create" {:resid "test" :organization "abc" :licenses [license-id]}
                                   api-key user-id))
        form-id (:id (api-call :post "/api/forms/create" {:organization "abc" :form/title "form update test" :fields []}
                               api-key user-id))
        workflow-id (:id (api-call :post "/api/workflows/create"
                                   {:organization "abc" :title "dynamic workflow"
                                    :type :dynamic :handlers [user-id]}
                                   api-key user-id))
        catalogue-id (:id (api-call :post "/api/catalogue-items/create"
                                    {:title "test-item-title"
                                     :form form-id
                                     :resid resource-id
                                     :wfid workflow-id}
                                    api-key user-id))]
    (is license-id)
    (is resource-id)
    (is form-id)
    (is workflow-id)
    (is catalogue-id)

    ;; no api for this yet
    (db/create-workflow-license! {:wfid workflow-id :licid license-id :round 0})

    (testing "can disable a resource"
      (is (:success (api-call :put "/api/resources/update" {:id resource-id :enabled false :archived false}
                              api-key user-id))))
    (testing "can't archive resource if it is part of an active catalogue item"
      (let [resp (api-call :put "/api/resources/update" {:id resource-id :enabled true :archived true}
                           api-key user-id)]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/resource-in-use"
                 :catalogue-items [{:id catalogue-id :title "test-item-title" :localizations nil}]}]
               (:errors resp)))))

    (testing "can disable a form"
      (is (:success (api-call :put "/api/forms/update" {:id form-id :enabled false :archived false}
                              api-key user-id))))
    (testing "can't archive a form that's in use"
      (let [resp (api-call :put "/api/forms/update" {:id form-id :enabled true :archived true}
                           api-key user-id)]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/form-in-use"
                 :catalogue-items [{:id catalogue-id :title "test-item-title" :localizations nil}]}]
               (:errors resp)))))

    (testing "can disable a workflow"
      (is (:success (api-call :put "/api/workflows/update" {:id workflow-id :enabled false :archived false}
                              api-key user-id))))
    (testing "can't archive a workflow that's in use"
      (let [resp (api-call :put "/api/workflows/update" {:id workflow-id :enabled true :archived true}
                           api-key user-id)]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/workflow-in-use"
                 :catalogue-items [{:id catalogue-id :title "test-item-title" :localizations nil}]}]
               (:errors resp)))))

    (testing "can disable a license"
      (is (:success (api-call :put "/api/licenses/update" {:id license-id :enabled false :archived false}
                              api-key user-id))))
    (testing "can't archive a license that's in use"
      (let [resp (api-call :put "/api/licenses/update" {:id license-id :enabled true :archived true}
                           api-key user-id)]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/license-in-use"
                 :resources [{:id resource-id :resid "test"}]
                 :workflows [{:id workflow-id :title "dynamic workflow"}]}]
               (:errors resp)))))

    (testing "can archive a catalogue item"
      (is (:success (api-call :put "/api/catalogue-items/update" {:id catalogue-id :enabled true :archived true}
                              api-key user-id))))
    (testing "can archive a resource that's not in use"
      (is (:success (api-call :put "/api/resources/update" {:id resource-id :enabled true :archived true}
                              api-key user-id))))
    (testing "can archive a form that's not in use"
      (is (:success (api-call :put "/api/forms/update" {:id form-id :enabled true :archived true}
                              api-key user-id))))
    (testing "can archive a workflow that's not in use"
      (is (:success (api-call :put "/api/workflows/update" {:id workflow-id :enabled true :archived true}
                              api-key user-id))))
    (testing "can archive a license that's not in use"
      (is (= {:success true} (api-call :put "/api/licenses/update" {:id license-id :enabled true :archived true}
                                       api-key user-id))))))
