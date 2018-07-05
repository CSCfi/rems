(ns rems.test.api.util
  (:require [clojure.test :refer [deftest is]]
            [rems.api.util :refer :all]))

(deftest longify-keys-test
  (is (= {} (longify-keys nil)))
  (is (= {42 42} (longify-keys {:42 42}))))
