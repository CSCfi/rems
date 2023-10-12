(ns rems.test-scheduler
  (:require [clj-time.core :as time-core]
            [clojure.test :refer [deftest is]]
            [rems.scheduler]
            [rems.testing-util :refer [with-fixed-time]]))

(deftest test-buzy-hours
  (let [side-effect (atom nil)]
    (with-fixed-time (time-core/date-time 2021 1 1 8 0 0)
      (#'rems.scheduler/task-wrapper #(reset! side-effect 1) nil)
      (is (= 1 @side-effect)))

    (with-fixed-time (time-core/date-time 2021 1 1 6 59 59)
      (#'rems.scheduler/task-wrapper #(reset! side-effect 2) {:buzy-hours [["07:00" "17:00"]]})
      (is (= 2 @side-effect)))

    (with-fixed-time (time-core/date-time 2021 1 1 8 0 0)
      (#'rems.scheduler/task-wrapper #(reset! side-effect 3) {:buzy-hours [["07:00" "17:00"]]})
      (is (= 2 @side-effect)
          "scheduled task is not executed during buzy hour"))

    (with-fixed-time (time-core/date-time 2021 1 1 17 0 0)
      (#'rems.scheduler/task-wrapper #(reset! side-effect 4) {:buzy-hours [["07:00" "17:00"]]})
      (is (= 4 @side-effect)))))
