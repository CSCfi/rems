(ns rems.test.guide
  "Smoke test for the rendering the component guide."
  (:require [clojure.test :refer :all]
            [rems.routes.guide :refer [guide-page]]
            [rems.db.applications :as applications]))

(deftest test-guide
  (with-redefs [applications/get-application-phases (fn [& args] [{:id 1 :phase :apply}])]
    (is (string? (guide-page)))))
