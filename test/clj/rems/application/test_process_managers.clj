(ns ^:integration rems.application.test-process-managers
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [rems.service.command :as command]
            [rems.api.testing :refer :all]
            [rems.db.attachments :as attachments]
            [rems.db.applications :as applications]
            [rems.service.test-data :as test-data]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures :each api-fixture)

(defn upload-request [app-id username filename]
  (let [testfile (io/file "./test-data/test.txt")
        file {:tempfile testfile
              :content-type "text/plain"
              :filename filename
              :size (.length testfile)}
        id (-> (request :post (str "/api/applications/add-attachment?application-id=" app-id))
               (assoc :multipart-params {"file" file})
               (authenticate "42" username)
               handler
               read-ok-body
               :id)]
    (is (number? id))
    id))

(defn- get-attachments [application]
  (->> application
       :application/attachments
       (sort-by :attachment/id)
       (mapv #(update % :attachment/user :userid))
       (mapv #(dissoc % :attachment/event :attachment/redact-roles))))

(deftest test-run-delete-orphan-attachments
  (binding [command/*fail-on-process-manager-errors* true]
    (test-data/create-test-api-key!)
    (test-helpers/create-user! {:userid "alice"})
    (test-helpers/create-user! {:userid "handler"})
    (let [res-id (test-helpers/create-resource! {:resource-ext-id "resource"})
          wf-id (test-helpers/create-workflow! {:type :workflow/default
                                                :handlers ["handler"]})
          form-id (test-helpers/create-form! {:form/fields [{:field/id "attachment1"
                                                             :field/title {:en "first attachment"
                                                                           :fi "first attachment"
                                                                           :sv "first attachment"}
                                                             :field/optional false
                                                             :field/type :attachment}
                                                            {:field/id "attachment2"
                                                             :field/title {:en "second attachment"
                                                                           :fi "second attachment"
                                                                           :sv "second attachment"}
                                                             :field/optional false
                                                             :field/type :attachment}]})
          cat-id (test-helpers/create-catalogue-item! {:title {:en "catalogue-item"}
                                                       :form-id form-id
                                                       :workflow-id wf-id
                                                       :resource-id res-id})
          app-id (test-helpers/create-application! {:actor "alice" :catalogue-item-ids [cat-id]})]

      (testing "create unrelated application"
        (let [unrelated-app-id (test-helpers/create-application! {:actor "alice" :catalogue-item-ids [cat-id]})
              unrelated-attachment-id (upload-request unrelated-app-id "alice" "attachment1.txt")]

          (is (= [{:attachment/id unrelated-attachment-id :attachment/filename "attachment1.txt" :attachment/type "text/plain" :attachment/user "alice"}]
                 (-> (applications/get-application-internal unrelated-app-id)
                     (get-attachments))
                 (attachments/get-attachments-for-application unrelated-app-id))
              "unrelated attachment was saved")

          (testing "in main application"
            (testing "upload attachment1"
              (let [attachment-id1 (upload-request app-id "alice" "attachment1.txt")]

                (testing "use attachment1 in a field"
                  (is (= {:success true
                          :warnings [{:type "t.form.validation/required"
                                      :form-id form-id
                                      :field-id "attachment2"}]}
                         (-> (request :post (str "/api/applications/save-draft"))
                             (authenticate "42" "alice")
                             (json-body {:application-id app-id
                                         :field-values [{:form form-id :field "attachment1" :value (str attachment-id1)}]})
                             handler
                             read-ok-body)))

                  (is (= [{:attachment/id attachment-id1 :attachment/filename "attachment1.txt" :attachment/type "text/plain" :attachment/user "alice"}]
                         (-> (applications/get-application-internal app-id)
                             (get-attachments))
                         (attachments/get-attachments-for-application app-id))
                      "attachment1 was saved"))

                (testing "upload attachment2"
                  (let [attachment-id2 (upload-request app-id "alice" "attachment2.txt")]

                    (testing "use attachment2 in a field"
                      (is (= {:success true}
                             (-> (request :post (str "/api/applications/save-draft"))
                                 (authenticate "42" "alice")
                                 (json-body {:application-id app-id
                                             :field-values [{:form form-id :field "attachment1" :value (str attachment-id1)}
                                                            {:form form-id :field "attachment2" :value (str attachment-id2)}]})
                                 handler
                                 read-ok-body)))

                      (is (= [{:attachment/id attachment-id1 :attachment/filename "attachment1.txt" :attachment/type "text/plain" :attachment/user "alice"}
                              {:attachment/id attachment-id2 :attachment/filename "attachment2.txt" :attachment/type "text/plain" :attachment/user "alice"}]
                             (-> (applications/get-application-internal app-id)
                                 (get-attachments))
                             (attachments/get-attachments-for-application app-id))
                          "attachment1 and attachment2 are saved"))

                    (testing "upload attachment3"
                      (let [_attachment-id3 (upload-request app-id "alice" "attachment3.txt")]

                        (testing "send application"
                          ;; NB: don't save again, so attachment3 shouldn't be in use

                          (is (= {:success true}
                                 (-> (request :post "/api/applications/submit")
                                     (authenticate "42" "alice")
                                     (json-body {:application-id app-id})
                                     handler
                                     read-ok-body)))

                          ;; NB: attachment 3 should have been cleaned

                          (is (= [{:attachment/id attachment-id1 :attachment/filename "attachment1.txt" :attachment/type "text/plain" :attachment/user "alice"}
                                  {:attachment/id attachment-id2 :attachment/filename "attachment2.txt" :attachment/type "text/plain" :attachment/user "alice"}]
                                 (-> (applications/get-application-internal app-id)
                                     (get-attachments))
                                 (attachments/get-attachments-for-application app-id))
                              "attachment1 and attachment2 are saved, but not attachment3"))))

                    (testing "return application with handler attachment comment"
                      (let [handler-attachment-id  (upload-request app-id "handler" "handler.txt")]

                        (is (= {:success true}
                               (-> (request :post "/api/applications/return")
                                   (authenticate "42" "handler")
                                   (json-body {:application-id app-id
                                               :attachments [{:attachment/id handler-attachment-id}]})
                                   handler
                                   read-ok-body)))

                        (is (= [{:attachment/id attachment-id1 :attachment/filename "attachment1.txt" :attachment/type "text/plain" :attachment/user "alice"}
                                {:attachment/id attachment-id2 :attachment/filename "attachment2.txt" :attachment/type "text/plain" :attachment/user "alice"}
                                {:attachment/id handler-attachment-id :attachment/filename "handler.txt" :attachment/type "text/plain" :attachment/user "handler"}]
                               (-> (applications/get-application-internal app-id)
                                   (get-attachments))
                               (attachments/get-attachments-for-application app-id))
                            "attachment1, attachment2 and handler attachment are saved")

                        (testing "replace attachment1 in a field"
                          (let [attachment-id4 (upload-request app-id "alice" "attachment4.txt")]

                            (is (= {:success true}
                                   (-> (request :post (str "/api/applications/save-draft"))
                                       (authenticate "42" "alice")
                                       (json-body {:application-id app-id
                                                   :field-values [{:form form-id :field "attachment1" :value (str attachment-id4)}
                                                                  {:form form-id :field "attachment2" :value (str attachment-id2)}]})
                                       handler
                                       read-ok-body)))

                            (is (= [{:attachment/id attachment-id1 :attachment/filename "attachment1.txt" :attachment/type "text/plain" :attachment/user "alice"} ; still here because it's the old value
                                    {:attachment/id attachment-id2 :attachment/filename "attachment2.txt" :attachment/type "text/plain" :attachment/user "alice"}
                                    {:attachment/id handler-attachment-id :attachment/filename "handler.txt" :attachment/type "text/plain" :attachment/user "handler"}
                                    {:attachment/id attachment-id4 :attachment/filename "attachment4.txt" :attachment/type "text/plain" :attachment/user "alice"}]
                                   (-> (applications/get-application-internal app-id)
                                       (get-attachments))
                                   (attachments/get-attachments-for-application app-id))
                                "attachment1, attachment2, attachment4 and handler attachment are saved")

                            (testing "upload attachment5"
                              (let [_attachment-id5 (upload-request app-id "alice" "attachment5.txt")]

                                (testing "send application again"
                                  ;; NB: don't save again, so attachment5 shouldn't be in use

                                  (is (= {:success true}
                                         (-> (request :post "/api/applications/submit")
                                             (authenticate "42" "alice")
                                             (json-body {:application-id app-id})
                                             handler
                                             read-ok-body)))

                                  ;; NB: attachment 5 should have been cleaned

                                  (is (= [{:attachment/id attachment-id1 :attachment/filename "attachment1.txt" :attachment/type "text/plain" :attachment/user "alice"} ; still here because it's the old value
                                          {:attachment/id attachment-id2 :attachment/filename "attachment2.txt" :attachment/type "text/plain" :attachment/user "alice"}
                                          {:attachment/id handler-attachment-id :attachment/filename "handler.txt" :attachment/type "text/plain" :attachment/user "handler"}
                                          {:attachment/id attachment-id4 :attachment/filename "attachment4.txt" :attachment/type "text/plain" :attachment/user "alice"}]
                                         (-> (applications/get-application-internal app-id)
                                             (get-attachments))
                                         (attachments/get-attachments-for-application app-id))
                                      "attachment1, attachment2, attachment4 and handler attachment are saved")

                                  (is (= [{:attachment/id unrelated-attachment-id :attachment/filename "attachment1.txt" :attachment/type "text/plain" :attachment/user "alice"}]
                                         (-> (applications/get-application-internal unrelated-app-id)
                                             (get-attachments))
                                         (attachments/get-attachments-for-application unrelated-app-id))
                                      "unrelated attachment is still there"))))))))))))))))))
