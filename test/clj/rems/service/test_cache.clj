(ns ^:integration rems.service.test-cache
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.service.cache :as cache]
            [rems.service.catalogue :as catalogue-item]
            [rems.service.user :as user]
            [rems.service.workflow :as workflow]
            [rems.testing-util :refer [with-user]])
  (:import [java.util UUID]))

(use-fixtures
  :once
  test-db-fixture
  rollback-db-fixture)

(deftest test-get-catalogue-item-licenses
  (let [form-id (test-helpers/create-form! {})]
    (testing "resource licenses"
      (let [lic-id (test-helpers/create-license! {})
            wf-id (test-helpers/create-workflow! {})
            res-id (test-helpers/create-resource! {:resource-ext-id (str (UUID/randomUUID))
                                                   :license-ids [lic-id]})
            cat-id (test-helpers/create-catalogue-item! {:resource-id res-id
                                                         :form-id form-id
                                                         :workflow-id wf-id})]
        (is (= [lic-id]
               (map :license/id (cache/get-catalogue-item-licenses cat-id))))))

    (testing "workflow licenses"
      (let [lic-id (test-helpers/create-license! {})
            wf-id (test-helpers/create-workflow! {:licenses [lic-id]})
            res-id (test-helpers/create-resource! {:resource-ext-id (str (UUID/randomUUID))})
            cat-id (test-helpers/create-catalogue-item! {:resource-id res-id
                                                         :form-id form-id
                                                         :workflow-id wf-id})]
        (is (= [lic-id]
               (map :license/id (cache/get-catalogue-item-licenses cat-id))))))))

(deftest test-get-full-internal-application
  (let [applicant-attrs {:userid "fatima" :name "Fatima Virtanen" :email "fatima@virtanen.name"}
        _applicant-user (test-helpers/create-user! applicant-attrs)
        handler-attrs1 {:userid "handler" :name "Hannah Handler" :email "hannah@handlers.org"}
        _handler-user1 (test-helpers/create-user! handler-attrs1)
        _handler-user2 (test-helpers/create-user! {:userid "handler2" :name "Hamza Handler" :email "hamza@handlers.org"})
        wf-id (test-helpers/create-workflow! {:handlers ["handler"]})
        cat-id (test-helpers/create-catalogue-item! {:workflow-id wf-id :title {:en "En title" :fi "Fi title" :sv "Sv title"}})
        app-id (test-helpers/create-application! {:actor "fatima" :catalogue-item-ids [cat-id]})
        app1 (cache/get-full-internal-application app-id)

        _ (test-helpers/fill-form! {:application-id app-id
                                    :actor "fatima"
                                    :field-value "here is my application"})
        app2 (cache/get-full-internal-application app-id)
        _ (is (not= app1 app2) "cache should update if application is changed")

        _ (test-helpers/accept-licenses! {:application-id app-id
                                          :actor "fatima"})
        app3 (cache/get-full-internal-application app-id)
        _ (is (not= app2 app3) "cache should update if application is changed")

        _ (test-helpers/command! {:type :application.command/submit
                                  :application-id app-id
                                  :actor "fatima"})
        app4 (cache/get-full-internal-application app-id)
        _ (is (not= app3 app4) "cache should update if application is changed")

        _ (user/edit-user! (assoc applicant-attrs :email "fatima@virtanen.fi"))
        app5 (cache/get-full-internal-application app-id)
        _ (is (not= app4 app5) "cache should update if an applicant attribute is changed")

        _ (user/edit-user! (assoc handler-attrs1 :email "hannah@new-handlers.org"))
        app6 (cache/get-full-internal-application app-id)
        _ (is (not= app5 app6) "cache should update if an applicant attribute is changed")

        _ (with-user "owner"
            (workflow/edit-workflow! {:id (:workflow/id (:application/workflow app6))
                                      :handlers ["handler2"]}))
        app7 (cache/get-full-internal-application app-id)
        _ (is (not= app6 app7) "cache should update if a wf-id is changed")

        ;; licenses can't actually be edited

        ;; forms can't actually be edited

        _ (with-user "owner"
            (catalogue-item/edit-catalogue-item! {:id cat-id
                                                  :localizations {:en {:title "En title new"}
                                                                  :fi {:title "Fi title new"}
                                                                  :sv {:title "Sv title new"}}}))
        app8 (cache/get-full-internal-application app-id)
        _ (is (not= app7 app8) "cache should update if a catalogue item is changed")
        ]))

;; TODO CACHE test projections are updated
