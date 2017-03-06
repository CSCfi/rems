(ns rems.test.guide
  "Smoke test for the rendering the component guide."
  (:require [clojure.test :refer :all]
            [rems.context :as context]
            [rems.routes.guide :refer [guide-page]]))

(deftest test-guide
  (binding [context/*root-path* "path/"]
    (is (string? (guide-page)))))
