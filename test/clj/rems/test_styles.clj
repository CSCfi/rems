(ns ^:integration rems.test-styles
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.config]
            [rems.context :as context]
            [rems.css.styles :as styles]))


(use-fixtures
  :once
  (fn [f]
    (mount/start #'rems.config/env)
    (f)
    (mount/stop)))

(deftest screen-css-smoke-test
  (binding [context/*lang* :fi]
    (is (string? (styles/screen-css)))))
