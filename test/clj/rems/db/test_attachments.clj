(ns ^:integration rems.db.test-attachments
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.cache :as cache]
            [rems.db.attachments]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.util :refer [to-bytes]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-attachments-cache
  (testing "cache reload works"
    (let [applicant (test-helpers/create-user! {:userid "alice"})
          cat-id (test-helpers/create-catalogue-item! {})
          app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id]
                                                    :actor applicant})
          attachment-1 (test-helpers/create-attachment! {:application-id app-id
                                                         :actor applicant})
          attachment-2 (test-helpers/create-attachment! {:application-id app-id
                                                         :actor applicant})]

      ;; force cache reload
      (cache/set-uninitialized! rems.db.attachments/attachment-cache)

      (is (= {attachment-1 {:application/id app-id
                            :attachment/id attachment-1
                            :attachment/filename "attachment.pdf"
                            :attachment/type "application/pdf"
                            :attachment/user applicant}
              attachment-2 {:application/id app-id
                            :attachment/id attachment-2
                            :attachment/filename "attachment (1).pdf"
                            :attachment/type "application/pdf"
                            :attachment/user applicant}}
             (into {} (cache/entries! rems.db.attachments/attachment-cache))))

      (testing "dependent caches"
        ;; verify that by-application-id is cached correctly (depends on attachment-cache)
        (is (= {app-id [{:application/id app-id
                         :attachment/id attachment-1
                         :attachment/filename "attachment.pdf"
                         :attachment/type "application/pdf"
                         :attachment/user applicant}
                        {:application/id app-id
                         :attachment/id attachment-2
                         :attachment/filename "attachment (1).pdf"
                         :attachment/type "application/pdf"
                         :attachment/user applicant}]}
               (into {} (cache/entries! @#'rems.db.attachments/by-application-id))))))))

(deftest test-license-attachments-cache
  (testing "cache reload works"
    (let [user-id (test-helpers/create-user! {:userid "alice"})
          attachment-id (rems.db.attachments/create-license-attachment!
                         {:user-id user-id
                          :filename "attachment.pdf"
                          :content-type "application/pdf"
                          :data (to-bytes "data")})]
      (cache/set-uninitialized! rems.db.attachments/license-attachments-cache)

      (is (= {attachment-id {:attachment/id attachment-id
                             :attachment/user user-id
                             :attachment/filename "attachment.pdf"
                             :attachment/type "application/pdf"}}
             (into {} (cache/entries! rems.db.attachments/license-attachments-cache)))))))
