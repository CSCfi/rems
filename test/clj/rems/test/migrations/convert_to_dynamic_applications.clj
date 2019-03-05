(ns ^:integration rems.test.migrations.convert-to-dynamic-applications
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.pprint :refer [pprint]]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [rems.db.applications :as applications]
            [rems.db.core :refer [*db*]]
            [rems.db.test-data :as test-data]
            [rems.db.workflow :as workflow]
            [rems.migrations.convert-to-dynamic-applications :refer :all]
            [rems.test.api :refer [api-fixture]]
            [rems.validate :as validate])
  (:import [java.util UUID]))

(use-fixtures
  :once
  api-fixture)

(defn add-more-test-data! []
  (jdbc/execute! *db* ["
INSERT INTO catalogue_item_application (id, applicantuserid, start, endt, modifieruserid, wfid, description) VALUES (20, 'alice', '2019-03-05 11:56:19.353103', null, null, 1, 'direct approval');
INSERT INTO catalogue_item_application (id, applicantuserid, start, endt, modifieruserid, wfid, description) VALUES (21, 'alice', '2019-03-05 11:56:59.596975', null, null, 2, 'third party review');
INSERT INTO catalogue_item_application (id, applicantuserid, start, endt, modifieruserid, wfid, description) VALUES (22, 'alice', '2019-03-05 11:58:25.793817', null, null, 2, 'withdraw');
INSERT INTO catalogue_item_application (id, applicantuserid, start, endt, modifieruserid, wfid, description) VALUES (23, 'alice', '2019-03-05 11:59:08.412314', null, null, 2, 'close');
INSERT INTO catalogue_item_application_items (catappid, catitemid) VALUES (20, 1);
INSERT INTO catalogue_item_application_items (catappid, catitemid) VALUES (21, 2);
INSERT INTO catalogue_item_application_items (catappid, catitemid) VALUES (22, 2);
INSERT INTO catalogue_item_application_items (catappid, catitemid) VALUES (23, 2);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (20, 'alice', 0, 'save', null, '2019-03-05 11:56:19.353103', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (20, 'alice', 0, 'save', null, '2019-03-05 11:56:32.083122', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (20, 'alice', 0, 'apply', null, '2019-03-05 11:56:32.397157', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (20, 'alice', 0, 'autoapprove', null, '2019-03-05 11:56:32.397157', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (21, 'alice', 0, 'save', null, '2019-03-05 11:56:59.596975', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (21, 'alice', 0, 'save', null, '2019-03-05 11:57:15.083212', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (21, 'alice', 0, 'apply', null, '2019-03-05 11:57:15.396081', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (21, 'carl', 0, 'review-request', 'plz comment', '2019-03-05 11:57:45.355694', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (21, 'carl', 0, 'third-party-review', 'lgtm', '2019-03-05 11:58:03.891460', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (22, 'alice', 0, 'save', null, '2019-03-05 11:58:25.793817', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (22, 'alice', 0, 'save', null, '2019-03-05 11:58:38.616129', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (22, 'alice', 0, 'apply', null, '2019-03-05 11:58:38.882900', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (22, 'alice', 0, 'withdraw', 'nope nope nope', '2019-03-05 11:58:47.882659', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (23, 'alice', 0, 'save', null, '2019-03-05 11:59:08.412314', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (23, 'alice', 0, 'save', null, '2019-03-05 11:59:17.267323', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (23, 'alice', 0, 'apply', null, '2019-03-05 11:59:17.559004', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (23, 'developer', 0, 'approve', '', '2019-03-05 11:59:42.328008', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (23, 'developer', 0, 'close', 'no more', '2019-03-05 11:59:54.974911', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (20, null, 1, 'alice', 'approved', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (20, null, 2, 'alice', 'approved', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (21, null, 1, 'alice', 'approved', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (21, null, 2, 'alice', 'approved', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (22, null, 1, 'alice', 'approved', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (22, null, 2, 'alice', 'approved', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (23, null, 1, 'alice', 'approved', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (23, null, 2, 'alice', 'approved', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (20, 1, 'alice', 'direct approval', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (20, 2, 'alice', 'direct approval', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (20, 3, 'alice', '', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (20, 4, 'alice', '', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (20, 5, 'alice', '', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (20, 6, 'alice', '', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (20, 7, 'alice', '', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (20, 8, 'alice', '', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 1, 'alice', 'third party review', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 2, 'alice', 'third party review', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 3, 'alice', '', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 4, 'alice', '', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 5, 'alice', '', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 6, 'alice', '', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 7, 'alice', '', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 8, 'alice', '', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 1, 'alice', 'withdraw', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 2, 'alice', 'withdraw', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 3, 'alice', '', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 4, 'alice', '', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 5, 'alice', '', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 6, 'alice', '', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 7, 'alice', '', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 8, 'alice', '', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 1, 'alice', 'close', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 2, 'alice', 'close', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 3, 'alice', '', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 4, 'alice', '', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 5, 'alice', '', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 6, 'alice', '', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 7, 'alice', '', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 8, 'alice', '', '2019-03-05 11:59:17.267323', null);
INSERT INTO entitlement (resid, catappid, userid, start, endt) VALUES (1, 20, 'alice', '2019-03-05 11:56:32.397157', null);
INSERT INTO entitlement (resid, catappid, userid, start, endt) VALUES (1, 23, 'alice', '2019-03-05 11:59:42.328008', '2019-03-05 11:59:54.974911');
"]))

(deftest test-migration
  (add-more-test-data!)
  (let [event-id-seq (atom (:currval (first (jdbc/query *db* ["select currval('application_event_id_seq')"]))))
        next-event-id #(swap! event-id-seq inc)]

    (let [applications (applications/get-applications-impl-batch "whatever" {})
          application-id 21
          application (->> applications
                           (filter #(= application-id (:id %)))
                           first)
          dynamic-workflows (->> (workflow/get-workflows {})
                                 (filter #(= "workflow/dynamic" (get-in % [:workflow :type]))))
          new-workflow (first dynamic-workflows)]
      (assert application)
      (assert (= 1 (count dynamic-workflows)))

      (println "--- before ---")
      (pprint application)
      (conman/with-transaction [*db* {:isolation :serializable}]
        (migrate-catalogue-items! (:id new-workflow))
        (migrate-application! 1 (:id new-workflow))
        (migrate-application! 2 (:id new-workflow))
        (migrate-application! 3 (:id new-workflow))
        (migrate-application! 4 (:id new-workflow))
        (migrate-application! 5 (:id new-workflow))
        (migrate-application! 8 (:id new-workflow))
        (migrate-application! 9 (:id new-workflow))
        (migrate-application! 10 (:id new-workflow))
        (migrate-application! 20 (:id new-workflow))
        (migrate-application! (:id application) (:id new-workflow)))
      (println "--- after ---")
      (pprint (applications/get-application-state (:id application)))
      ;; validation already happens when the events are written, but just in case...
      (is (empty? (validate/validate))))

    (let [app-id 1
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "draft application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "draft application"
                                                           2 "draft application"
                                                           3 "draft application"
                                                           4 ""
                                                           5 "draft application"
                                                           6 "draft application"
                                                           7 "draft appl"
                                                           8 "draft application"}
                                :application/accepted-licenses #{1 2}}]
              :state :rems.workflow.dynamic/draft
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))

    (let [app-id 2
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "applied application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "applied application"
                                                           2 "applied application"
                                                           3 "applied application"
                                                           4 ""
                                                           5 "applied application"
                                                           6 "applied application"
                                                           7 "applied ap"
                                                           8 "applied application"}
                                :application/accepted-licenses #{1 2}}
                               {:event/type :application.event/submitted
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}]
              :state :rems.workflow.dynamic/submitted
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))

    (let [app-id 3
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "rejected application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "rejected application"
                                                           2 "rejected application"
                                                           3 "rejected application"
                                                           4 ""
                                                           5 "rejected application"
                                                           6 "rejected application"
                                                           7 "rejected a"
                                                           8 "rejected application"}
                                :application/accepted-licenses #{1 2}}
                               {:event/type :application.event/submitted
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/rejected
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "comment for rejection"}]
              :state :rems.workflow.dynamic/rejected
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))

    (let [app-id 4
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "accepted application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "accepted application"
                                                           2 "accepted application"
                                                           3 "accepted application"
                                                           4 ""
                                                           5 "accepted application"
                                                           6 "accepted application"
                                                           7 "accepted a"
                                                           8 "accepted application"}
                                :application/accepted-licenses #{1 2}}
                               {:event/type :application.event/submitted
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/approved
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "comment for approval"}]
              :state :rems.workflow.dynamic/approved
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))

    (let [app-id 5
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "returned application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "returned application"
                                                           2 "returned application"
                                                           3 "returned application"
                                                           4 ""
                                                           5 "returned application"
                                                           6 "returned application"
                                                           7 "returned a"
                                                           8 "returned application"}
                                :application/accepted-licenses #{1 2}}
                               {:event/type :application.event/submitted
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/returned
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "comment for return"}]
              :state :rems.workflow.dynamic/returned
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))

    (let [app-id 8
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "bundled application"
              :applicantuserid "alice"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "alice"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}
                                                        {:catalogue-item/id 3
                                                         :resource/ext-id "Extra Data"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}
                                                       {:license/id 3}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "alice"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "bundled application"
                                                           2 "bundled application"
                                                           3 "bundled application"
                                                           4 ""
                                                           5 "bundled application"
                                                           6 "bundled application"
                                                           7 "bundled ap"
                                                           8 "bundled application"}
                                :application/accepted-licenses #{1 2 3}}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/returned
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "comment for return"}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 4) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}]
              :state :rems.workflow.dynamic/submitted
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))

    (let [app-id 9
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "application with review"
              :applicantuserid "alice"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "alice"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 4
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "alice"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "application with review"
                                                           2 "application with review"
                                                           3 "application with review"
                                                           4 ""
                                                           5 "application with review"
                                                           6 "application with review"
                                                           7 "applicatio"
                                                           8 "application with review"}
                                :application/accepted-licenses #{1 2}}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/commented
                                :event/actor "carl"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/request-id (UUID/fromString "00000000-0000-0000-0000-000000000000")
                                :application/comment "comment for review"}
                               {:event/type :application.event/approved
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 4) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "comment for approval"}]
              :state :rems.workflow.dynamic/approved
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))

    (let [app-id 10
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "application in review"
              :applicantuserid "alice"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "alice"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 4
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "alice"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "application in review"
                                                           2 "application in review"
                                                           3 "application in review"
                                                           4 ""
                                                           5 "application in review"
                                                           6 "application in review"
                                                           7 "applicatio"
                                                           8 "application in review"}
                                :application/accepted-licenses #{1 2}}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}]
              :state :rems.workflow.dynamic/submitted
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))

    (let [app-id 20
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "direct approval"
              :applicantuserid "alice"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 0) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 1
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 1) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "direct approval"
                                                           2 "direct approval"
                                                           3 ""
                                                           4 ""
                                                           5 ""
                                                           6 ""
                                                           7 ""
                                                           8 ""}
                                :application/accepted-licenses #{1 2}}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/approved
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment ""}]
              :state :rems.workflow.dynamic/approved
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))

    (let [app-id 21
          application (applications/get-application-state app-id)
          request-id (-> application :dynamic-events (nth 3) :application/request-id)]
      (is request-id)
      (is (= {:id app-id
              :description "third party review"
              :applicantuserid "alice"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 0) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 1) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "third party review"
                                                           2 "third party review"
                                                           3 ""
                                                           4 ""
                                                           5 ""
                                                           6 ""
                                                           7 ""
                                                           8 ""}
                                :application/accepted-licenses #{1 2}}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/comment-requested
                                :event/actor "carl" ;; TODO: should really be "developer" but the old event doesn't tell it
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/request-id request-id
                                :application/commenters ["carl"]
                                :application/comment "plz comment"}
                               {:event/type :application.event/commented
                                :event/actor "carl"
                                :event/time (-> application :dynamic-events (nth 4) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/request-id request-id
                                :application/comment "lgtm"}]
              :state :rems.workflow.dynamic/submitted
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))))

(comment
  (applications/get-application-state 2)
  (user/run-tests 'rems.test.migrations.convert-to-dynamic-applications))
