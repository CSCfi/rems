(ns ^:integration rems.service.test-resources
  (:require [clojure.test :refer :all]
            [rems.service.licenses]
            [rems.service.resource]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.testing-util :refer [with-user]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(defn- status-flags [res-id]
  (with-user "owner"
    (-> (rems.service.resource/get-resource res-id)
        (select-keys [:enabled :archived]))))

(deftest resource-enabled-archived-test
  (test-helpers/create-user! {:userid "owner"} :owner)
  (with-user "owner"
    (let [_org (test-helpers/create-organization! {})
          lic-id (test-helpers/create-license! {})
          res-id (test-helpers/create-resource! {:license-ids [lic-id]})
          res-id2 (test-helpers/create-resource! {})

          archive-license! #(with-user "owner"
                              (rems.service.licenses/set-license-archived! {:id lic-id
                                                                            :archived %}))]

      (testing "new resources are enabled and not archived"
        (is (= {:enabled true
                :archived false}
               (status-flags res-id))))

      ;; reset all to false for the following tests
      (rems.service.resource/set-resource-enabled! {:id res-id
                                                    :enabled false})
      (rems.service.resource/set-resource-archived! {:id res-id
                                                     :archived false})

      (testing "enable"
        (rems.service.resource/set-resource-enabled! {:id res-id
                                                      :enabled true})
        (is (= {:enabled true
                :archived false}
               (status-flags res-id))))

      (testing "disable"
        (rems.service.resource/set-resource-enabled! {:id res-id
                                                      :enabled false})
        (is (= {:enabled false
                :archived false}
               (status-flags res-id))))

      (testing "archive"
        (rems.service.resource/set-resource-archived! {:id res-id
                                                       :archived true})
        (is (= {:enabled false
                :archived true}
               (status-flags res-id))))

      (testing "unarchive"
        (rems.service.resource/set-resource-archived! {:id res-id
                                                       :archived false})
        (is (= {:enabled false
                :archived false}
               (status-flags res-id))))

      (testing "cannot unarchive if license is archived"
        (rems.service.resource/set-resource-archived! {:id res-id
                                                       :archived true})
        (archive-license! true)
        (is (not (:success (rems.service.resource/set-resource-archived! {:id res-id
                                                                          :archived false}))))
        (archive-license! false)
        (is (:success (rems.service.resource/set-resource-archived! {:id res-id
                                                                     :archived false}))))

      (testing "does not affect unrelated resources"
        (rems.service.resource/set-resource-enabled! {:id res-id
                                                      :enabled true})
        (rems.service.resource/set-resource-archived! {:id res-id
                                                       :archived true})
        (rems.service.resource/set-resource-enabled! {:id res-id2
                                                      :enabled false})
        (rems.service.resource/set-resource-archived! {:id res-id2
                                                       :archived false})
        (is (= {:enabled true
                :archived true}
               (status-flags res-id)))
        (is (= {:enabled false
                :archived false}
               (status-flags res-id2)))))))
