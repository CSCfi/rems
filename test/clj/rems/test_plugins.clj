(ns rems.test-plugins
  (:require [clojure.test :refer [deftest is testing]]
            [rems.config :refer [env]]
            [rems.plugins :as plugins]))

(deftest test-transform
  (testing "without plugins configured"
    (with-redefs [env {}]
      (is (= {} (plugins/transform :test {})))
      (is (= {:anything 52} (plugins/transform :test {:anything 52})))))

  (testing "with identity plugin"
    (with-redefs [env {:plugins [{:id :plugin/test
                                  :filename "test-data/identity-plugin.md"}]
                       :extension-points {:test [:plugin/test]}}]
      (is (= {} (plugins/transform :test {})))
      (is (= {:anything 52} (plugins/transform :test {:anything 52})))))

  (testing "with increment plugin"
    (with-redefs [env {:plugins [{:id :plugin/test
                                  :filename "test-data/increment-plugin.md"}]
                       :extension-points {:test [:plugin/test]}}]
      (is (= {:value 2} (plugins/transform :test {:value 1})))))

  (testing "with compile failing plugin"
    (with-redefs [env {:plugins [{:id :plugin/test
                                  :filename "test-data/failing-plugin.md"}]
                       :extension-points {:test [:plugin/test]}}]
      (try
        (plugins/transform :test {})

        (catch clojure.lang.ExceptionInfo e
          (is (= {:type :sci/error
                  :line 9
                  :column 4
                  :message "Could not resolve symbol: invalid"
                  :file nil
                  :phase "analysis"}
                 (dissoc (ex-data e) :sci.impl/callstack))
              "code analysis will fail and the location is reported")))))

  (testing "with exception throwing plugin"
    (with-redefs [env {:plugins [{:id :plugin/test
                                  :filename "test-data/exception-plugin.md"}]
                       :extension-points {:test [:plugin/test]}}]
      (try
        (plugins/transform :test {})

        (catch clojure.lang.ExceptionInfo e
          (is (= {:type :sci/error
                  :line 7
                  :column 1
                  :message "hello from plugin"
                  :file nil}
                 (dissoc (ex-data e) :sci.impl/callstack))
              "the exception is wrapped fail and returned"))))))

(deftest test-validate
  (testing "with validating plugin"
    (with-redefs [env {:plugins [{:id :plugin/fail-odd
                                  :filename "test-data/fail-odd-plugin.md"}
                                 {:id :plugin/fail-neg
                                  :filename "test-data/fail-neg-plugin.md"}]
                       :extension-points {:test [:plugin/fail-odd :plugin/fail-neg]}}]
      (testing "failing at runtime"
        (try
          (plugins/validate :test {})

          (catch clojure.lang.ExceptionInfo e
            (is (= {:type :sci/error
                    :line 6
                    :column 7
                    :message "Argument must be an integer: "
                    :file nil}
                   (dissoc (ex-data e) :sci.impl/callstack))
                "code will fail at runtime and the location is reported"))))

      (is (= nil (plugins/validate :test {:value 2}))
          "pass both validations")

      (is (= [{:result :fail :reason :odd}] (plugins/validate :test {:value 1}))
          "first validation fails")

      (is (= [{:result :fail :reason :neg}] (plugins/validate :test {:value -2}))
          "first validation is a pass but the second fails")

      (is (= [{:result :fail :reason :odd}]
             (plugins/validate :test {:value -1}))
          "the first validator to return errors is where the execution stops (short-circuit evaluation)"))))

