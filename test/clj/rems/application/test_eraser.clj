(ns ^:integration rems.application.test-eraser
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.core :as db]
            [rems.db.events :as events]
            [rems.db.applications :as applications]
            [rems.application.eraser :as eraser]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(defn- add-dummy-application!
  [{:keys [date-time actor draft?]}]
  (let [app-id (:id (db/create-application!))
        created-event {:event/type :application.event/created
                       :event/time date-time
                       :event/actor actor
                       :application/id app-id
                       :application/external-id ""
                       :application/resources []
                       :application/licenses []
                       :application/forms []
                       :workflow/id 0
                       :workflow/type :workflow/default}
        draft-saved-event {:event/type :application.event/draft-saved
                           :event/time date-time
                           :event/actor actor
                           :application/id app-id
                           :application/field-values []}
        submitted-event {:event/type :application.event/submitted
                         :event/time date-time
                         :event/actor actor
                         :application/id app-id}]
    (events/add-event! created-event)
    (events/add-event! draft-saved-event)
    (when (not draft?)
      (events/add-event! submitted-event))
    app-id))

(deftest test-eraser
  (testing "removes expired draft applications"
    (let [app-id-1 (add-dummy-application! {:draft? true :date-time (time/now) :actor "alice"})
          app-id-2 (add-dummy-application! {:date-time (time/now) :actor "alice"})
          app-id-3 (add-dummy-application! {:date-time (time/minus (time/now) (time/days 120)) :actor "alice"})
          expired-app-id (add-dummy-application! {:draft? true
                                                  :date-time (time/minus (time/now) (time/days 90) (time/seconds 1))
                                                  :actor "alice"})
          expired-app-id-2 (add-dummy-application! {:draft? true
                                                    :date-time (time/minus (time/now) (time/days 120))
                                                    :actor "alice"})]
      (let [before-apps (applications/get-all-applications "alice")]
        (is (= #{app-id-1 app-id-2 app-id-3 expired-app-id expired-app-id-2}
               (set (map :application/id before-apps)))))
      (eraser/remove-expired-applications!)
      (let [after-apps (applications/get-all-applications "alice")]
        (is (= #{app-id-1 app-id-2 app-id-3}
               (set (map :application/id after-apps))))))))