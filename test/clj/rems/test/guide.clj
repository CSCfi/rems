(ns rems.test.guide
  "Smoke test for the rendering the component guide."
  (:require [clojure.test :refer :all]
            [rems.routes.guide :refer [guide-page]]))

(deftest test-guide
  (is (string? (guide-page))))
