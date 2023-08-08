(ns rems.service.cache
  (:require [clojure.test :refer [deftest is testing]]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.service.cache :as cache])
  (:import [java.util UUID]))

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

