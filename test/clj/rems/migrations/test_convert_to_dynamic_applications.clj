(ns ^:integration rems.migrations.test-convert-to-dynamic-applications
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.pprint :refer [pprint]]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [rems.api.testing :refer [api-fixture]]
            [rems.db.applications.legacy :as legacy]
            [rems.db.core :refer [*db*]]
            [rems.db.test-data :as test-data]
            [rems.db.workflow :as workflow]
            [rems.migrations.convert-to-dynamic-applications :refer :all]
            [rems.validate :as validate])
  (:import [java.util UUID]))

(use-fixtures
  :once
  api-fixture)

;; dates in the past to avoid external-id conflicts with test-data
(defn add-more-test-data! []
  (jdbc/execute! *db* ["
INSERT INTO catalogue_item_application (id, applicantuserid, start, endt, modifieruserid, wfid, description) VALUES (21, 'alice', '2019-03-05 11:56:19.353103', null, null, 1, 'direct approval');
INSERT INTO catalogue_item_application (id, applicantuserid, start, endt, modifieruserid, wfid, description) VALUES (22, 'alice', '2019-03-05 11:56:59.596975', null, null, 2, 'third party review');
INSERT INTO catalogue_item_application (id, applicantuserid, start, endt, modifieruserid, wfid, description) VALUES (23, 'alice', '2019-03-05 11:58:25.793817', null, null, 2, 'withdraw');
INSERT INTO catalogue_item_application (id, applicantuserid, start, endt, modifieruserid, wfid, description) VALUES (24, 'alice', '2019-03-05 11:59:08.412314', null, null, 2, 'close');
INSERT INTO catalogue_item_application_items (catappid, catitemid) VALUES (21, 1);
INSERT INTO catalogue_item_application_items (catappid, catitemid) VALUES (22, 2);
INSERT INTO catalogue_item_application_items (catappid, catitemid) VALUES (23, 2);
INSERT INTO catalogue_item_application_items (catappid, catitemid) VALUES (24, 2);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (21, 'alice', 0, 'save', null, '2019-03-05 11:56:19.353103', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (21, 'alice', 0, 'save', null, '2019-03-05 11:56:32.083122', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (21, 'alice', 0, 'apply', null, '2019-03-05 11:56:32.397157', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (21, 'alice', 0, 'autoapprove', null, '2019-03-05 11:56:32.397157', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (22, 'alice', 0, 'save', null, '2019-03-05 11:56:59.596975', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (22, 'alice', 0, 'save', null, '2019-03-05 11:57:15.083212', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (22, 'alice', 0, 'apply', null, '2019-03-05 11:57:15.396081', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (22, 'carl', 0, 'review-request', 'plz comment', '2019-03-05 11:57:45.355694', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (22, 'carl', 0, 'third-party-review', 'lgtm', '2019-03-05 11:58:03.891460', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (23, 'alice', 0, 'save', null, '2019-03-05 11:58:25.793817', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (23, 'alice', 0, 'save', null, '2019-03-05 11:58:38.616129', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (23, 'alice', 0, 'apply', null, '2019-03-05 11:58:38.882900', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (23, 'alice', 0, 'withdraw', 'nope nope nope', '2019-03-05 11:58:47.882659', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (24, 'alice', 0, 'save', null, '2019-03-05 11:59:08.412314', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (24, 'alice', 0, 'save', null, '2019-03-05 11:59:17.267323', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (24, 'alice', 0, 'apply', null, '2019-03-05 11:59:17.559004', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (24, 'developer', 0, 'approve', '', '2019-03-05 11:59:42.328008', null);
INSERT INTO application_event (appid, userid, round, event, comment, time, eventdata) VALUES (24, 'developer', 0, 'close', 'no more', '2019-03-05 11:59:54.974911', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (21, null, 1, 'alice', 'approved', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (21, null, 2, 'alice', 'approved', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (22, null, 1, 'alice', 'approved', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (22, null, 2, 'alice', 'approved', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (23, null, 1, 'alice', 'approved', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (23, null, 2, 'alice', 'approved', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (24, null, 1, 'alice', 'approved', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_license_approval_values (catappid, formmapid, licid, modifieruserid, state, start, endt) VALUES (24, null, 2, 'alice', 'approved', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 1, 'alice', 'direct approval', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 2, 'alice', 'direct approval', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 3, 'alice', '', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 4, 'alice', '', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 5, 'alice', '', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 6, 'alice', '', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 7, 'alice', '', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (21, 8, 'alice', '', '2019-03-05 11:56:32.083122', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 1, 'alice', 'third party review', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 2, 'alice', 'third party review', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 3, 'alice', '', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 4, 'alice', '', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 5, 'alice', '', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 6, 'alice', '', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 7, 'alice', '', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (22, 8, 'alice', '', '2019-03-05 11:57:15.083212', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 1, 'alice', 'withdraw', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 2, 'alice', 'withdraw', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 3, 'alice', '', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 4, 'alice', '', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 5, 'alice', '', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 6, 'alice', '', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 7, 'alice', '', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (23, 8, 'alice', '', '2019-03-05 11:58:38.616129', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (24, 1, 'alice', 'close', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (24, 2, 'alice', 'close', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (24, 3, 'alice', '', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (24, 4, 'alice', '', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (24, 5, 'alice', '', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (24, 6, 'alice', '', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (24, 7, 'alice', '', '2019-03-05 11:59:17.267323', null);
INSERT INTO application_text_values (catappid, formmapid, modifieruserid, value, start, endt) VALUES (24, 8, 'alice', '', '2019-03-05 11:59:17.267323', null);
INSERT INTO entitlement (resid, catappid, userid, start, endt) VALUES (1, 21, 'alice', '2019-03-05 11:56:32.397157', null);
INSERT INTO entitlement (resid, catappid, userid, start, endt) VALUES (1, 24, 'alice', '2019-03-05 11:59:42.328008', '2019-03-05 11:59:54.974911');
"]))

(deftest test-migration
  (add-more-test-data!)
  (let [event-id-seq (atom (:currval (first (jdbc/query *db* ["select currval('application_event_id_seq')"]))))
        next-event-id #(swap! event-id-seq inc)]

    (let [dynamic-workflows (->> (workflow/get-workflows {})
                                 (filter #(= "workflow/dynamic" (get-in % [:workflow :type]))))
          new-workflow (first dynamic-workflows)]
      (assert (= 1 (count dynamic-workflows)))
      (assert new-workflow)
      (conman/with-transaction [*db* {:isolation :serializable}]
        (migrate-catalogue-items! (:id new-workflow))
        (migrate-application! 1 (:id new-workflow))
        (migrate-application! 2 (:id new-workflow))
        (migrate-application! 3 (:id new-workflow))
        (migrate-application! 4 (:id new-workflow))
        (migrate-application! 5 (:id new-workflow))
        (migrate-application! 6 (:id new-workflow))
        (migrate-application! 7 (:id new-workflow))
        (migrate-application! 8 (:id new-workflow))
        (migrate-application! 21 (:id new-workflow))
        (migrate-application! 22 (:id new-workflow))
        (migrate-application! 23 (:id new-workflow))
        (migrate-application! 24 (:id new-workflow)))
      ;; validation already happens when the events are written, but just in case...
      (is (nil? (validate/validate))))

    (let [app-id 1
          application (legacy/get-application-state app-id)]
      (is (= {:id app-id
              :description "draft application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/external-id "2019/11"
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "draft application"
                                                           2 "draft application"
                                                           3 "draft application"
                                                           4 "draft application"
                                                           5 ""
                                                           6 "draft application"
                                                           7 "draft application"
                                                           8 "draft appl"
                                                           9 "draft application"}}
                               {:event/type :application.event/licenses-accepted
                                :event/time test-data/creation-time
                                :event/actor "developer"
                                :application/id 1
                                :application/accepted-licenses #{1 2}
                                :event/id (next-event-id)}]
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :workflow]))))

    (let [app-id 2
          application (legacy/get-application-state app-id)]
      (is (= {:id app-id
              :description "applied application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/external-id "2019/12"
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "applied application"
                                                           2 "applied application"
                                                           3 "applied application"
                                                           4 "applied application"
                                                           5 ""
                                                           6 "applied application"
                                                           7 "applied application"
                                                           8 "applied ap"
                                                           9 "applied application"}}
                               {:event/type :application.event/licenses-accepted
                                :event/time test-data/creation-time
                                :event/actor "developer"
                                :application/id app-id
                                :application/accepted-licenses #{1 2}
                                :event/id (next-event-id)}
                               {:event/type :application.event/submitted
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}]
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :workflow]))))

    (let [app-id 3
          application (legacy/get-application-state app-id)]
      (is (= {:id app-id
              :description "rejected application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/external-id "2019/13"
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "rejected application"
                                                           2 "rejected application"
                                                           3 "rejected application"
                                                           4 "rejected application"
                                                           5 ""
                                                           6 "rejected application"
                                                           7 "rejected application"
                                                           8 "rejected a"
                                                           9 "rejected application"}}
                               {:event/type :application.event/licenses-accepted
                                :event/time test-data/creation-time
                                :event/actor "developer"
                                :application/id app-id
                                :application/accepted-licenses #{1 2}
                                :event/id (next-event-id)}
                               {:event/type :application.event/submitted
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/rejected
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 4) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "comment for rejection"}]
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :workflow]))))

    (let [app-id 4
          application (legacy/get-application-state app-id)]
      (is (= {:id app-id
              :description "accepted application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/external-id "2019/14"
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "accepted application"
                                                           2 "accepted application"
                                                           3 "accepted application"
                                                           4 "accepted application"
                                                           5 ""
                                                           6 "accepted application"
                                                           7 "accepted application"
                                                           8 "accepted a"
                                                           9 "accepted application"}}
                               {:event/type :application.event/licenses-accepted
                                :event/time test-data/creation-time
                                :event/actor "developer"
                                :application/id 4
                                :application/accepted-licenses #{1 2}
                                :event/id (next-event-id)}
                               {:event/type :application.event/submitted
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/approved
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 4) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "comment for approval"}]
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :workflow]))))

    (let [app-id 5
          application (legacy/get-application-state app-id)]
      (is (= {:id app-id
              :description "returned application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/external-id "2019/15"
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "returned application"
                                                           2 "returned application"
                                                           3 "returned application"
                                                           4 "returned application"
                                                           5 ""
                                                           6 "returned application"
                                                           7 "returned application"
                                                           8 "returned a"
                                                           9 "returned application"}}
                               {:event/type :application.event/licenses-accepted
                                :event/time test-data/creation-time
                                :event/actor "developer"
                                :application/id app-id
                                :application/accepted-licenses #{1 2}
                                :event/id (next-event-id)}
                               {:event/type :application.event/submitted
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/returned
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 4) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "comment for return"}]
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :workflow]))))

    (let [app-id 6
          application (legacy/get-application-state app-id)]
      (is (= {:id app-id
              :description "bundled application"
              :applicantuserid "alice"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "alice"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/external-id "2019/16"
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}
                                                        {:catalogue-item/id 3
                                                         :resource/ext-id "Extra Data"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}
                                                       {:license/id 3}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic}
                               {:event/type :application.event/draft-saved
                                :event/actor "alice"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "bundled application"
                                                           2 "bundled application"
                                                           3 "bundled application"
                                                           4 "bundled application"
                                                           5 ""
                                                           6 "bundled application"
                                                           7 "bundled application"
                                                           8 "bundled ap"
                                                           9 "bundled application"}}
                               {:event/type :application.event/licenses-accepted
                                :event/time test-data/creation-time
                                :event/actor "alice"
                                :application/id app-id
                                :application/accepted-licenses #{1 2 3}
                                :event/id (next-event-id)}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/returned
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 4) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "comment for return"}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 5) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}]
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :workflow]))))

    (let [app-id 7
          application (legacy/get-application-state app-id)]
      (is (= {:id app-id
              :description "application with review"
              :applicantuserid "alice"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "alice"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/external-id "2019/17"
                                :application/resources [{:catalogue-item/id 4
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic}
                               {:event/type :application.event/draft-saved
                                :event/actor "alice"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "application with review"
                                                           2 "application with review"
                                                           3 "application with review"
                                                           4 "application with review"
                                                           5 ""
                                                           6 "application with review"
                                                           7 "application with review"
                                                           8 "applicatio"
                                                           9 "application with review"}}
                               {:event/type :application.event/licenses-accepted
                                :event/time test-data/creation-time
                                :event/actor "alice"
                                :application/id app-id
                                :application/accepted-licenses #{1 2}
                                :event/id (next-event-id)}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/commented
                                :event/actor "carl"
                                :event/time (-> application :dynamic-events (nth 4) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/request-id (UUID/fromString "00000000-0000-0000-0000-000000000000")
                                :application/comment "comment for review"}
                               {:event/type :application.event/approved
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 5) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "comment for approval"}]
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :workflow]))))

    (let [app-id 8
          application (legacy/get-application-state app-id)]
      (is (= {:id app-id
              :description "application in review"
              :applicantuserid "alice"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "alice"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/external-id "2019/18"
                                :application/resources [{:catalogue-item/id 4
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic}
                               {:event/type :application.event/draft-saved
                                :event/actor "alice"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "application in review"
                                                           2 "application in review"
                                                           3 "application in review"
                                                           4 "application in review"
                                                           5 ""
                                                           6 "application in review"
                                                           7 "application in review"
                                                           8 "applicatio"
                                                           9 "application in review"}}
                               {:event/type :application.event/licenses-accepted
                                :event/time test-data/creation-time
                                :event/actor "alice"
                                :application/id app-id
                                :application/accepted-licenses #{1 2}
                                :event/id (next-event-id)}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}]
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :workflow]))))

    (let [app-id 21
          application (legacy/get-application-state app-id)]
      (is (= {:id app-id
              :description "direct approval"
              :applicantuserid "alice"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 0) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/external-id "2019/19"
                                :application/resources [{:catalogue-item/id 1
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic}
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
                                                           8 ""
                                                           9 ""}}
                               {:event/type :application.event/licenses-accepted
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/actor "alice"
                                :application/id app-id
                                :application/accepted-licenses #{1 2}
                                :event/id (next-event-id)}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/approved
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 4) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment ""}]
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :workflow]))))

    (let [app-id 22
          application (legacy/get-application-state app-id)
          request-id (-> application :dynamic-events (nth 4) :application/request-id)]
      (is request-id)
      (is (= {:id app-id
              :description "third party review"
              :applicantuserid "alice"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 0) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/external-id "2019/20"
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic}
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
                                                           8 ""
                                                           9 ""}}
                               {:event/type :application.event/licenses-accepted
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/actor "alice"
                                :application/id app-id
                                :application/accepted-licenses #{1 2}
                                :event/id (next-event-id)}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/comment-requested
                                :event/actor "carl" ;; TODO: should really be "developer" but the old event doesn't tell it
                                :event/time (-> application :dynamic-events (nth 4) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/request-id request-id
                                :application/commenters ["carl"]
                                :application/comment "plz comment"}
                               {:event/type :application.event/commented
                                :event/actor "carl"
                                :event/time (-> application :dynamic-events (nth 5) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/request-id request-id
                                :application/comment "lgtm"}]
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :workflow]))))

    (let [app-id 23
          application (legacy/get-application-state app-id)]
      (is (= {:id app-id
              :description "withdraw"
              :applicantuserid "alice"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 1) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/external-id "2019/21"
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic}
                               {:event/type :application.event/draft-saved
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 1) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "withdraw"
                                                           2 "withdraw"
                                                           3 ""
                                                           4 ""
                                                           5 ""
                                                           6 ""
                                                           7 ""
                                                           8 ""
                                                           9 ""}}
                               {:event/type :application.event/licenses-accepted
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/actor "alice"
                                :application/id app-id
                                :application/accepted-licenses #{1 2}
                                :event/id (next-event-id)}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/returned
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 4) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "nope nope nope"}]
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :workflow]))))

    (let [app-id 24
          application (legacy/get-application-state app-id)]
      (is (= {:id app-id
              :description "close"
              :applicantuserid "alice"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 1) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/external-id "2019/22"
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic}
                               {:event/type :application.event/draft-saved
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 1) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "close"
                                                           2 "close"
                                                           3 ""
                                                           4 ""
                                                           5 ""
                                                           6 ""
                                                           7 ""
                                                           8 ""
                                                           9 ""}}
                               {:event/type :application.event/licenses-accepted
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/actor "alice"
                                :application/id app-id
                                :application/accepted-licenses #{1 2}
                                :event/id (next-event-id)}
                               {:event/type :application.event/submitted
                                :event/actor "alice"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/approved
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 4) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment ""}
                               {:event/type :application.event/closed
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 5) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "no more"}]
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :workflow]))))))

(comment
  (user/run-tests 'rems.migrations.test-convert-to-dynamic-applications))
