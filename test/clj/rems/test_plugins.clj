(ns rems.test-plugins
  (:require [clojure.test :refer [deftest is testing]]
            [rems.config :refer [env]]
            [rems.json :as json]
            [rems.plugins :as plugins]
            [stub-http.core :as stub]))

(deftest test-transform
  (testing "without plugins configured"
    (with-redefs [env {}]
      (#'plugins/load-plugin-configs!)
      (#'plugins/load-plugins!)
      (is (= {} (plugins/transform :test {})))
      (is (= {:anything 52} (plugins/transform :test {:anything 52})))))

  (testing "with identity plugin"
    (with-redefs [env {:plugins [{:id :plugin/test
                                  :filename "test-data/identity-plugin.md"}]
                       :extension-points {:test [:plugin/test]}}]
      (#'plugins/load-plugin-configs!)
      (#'plugins/load-plugins!)
      (is (= {} (plugins/transform :test {})))
      (is (= {:anything 52} (plugins/transform :test {:anything 52})))))

  (testing "with increment plugin"
    (with-redefs [env {:plugins [{:id :plugin/test
                                  :filename "test-data/increment-plugin.md"}]
                       :extension-points {:test [:plugin/test]}}]
      (#'plugins/load-plugin-configs!)
      (#'plugins/load-plugins!)
      (is (= {:value 2} (plugins/transform :test {:value 1})))))

  (testing "with compile failing plugin"
    (with-redefs [env {:plugins [{:id :plugin/test
                                  :filename "test-data/failing-plugin.md"}]
                       :extension-points {:test [:plugin/test]}}]
      (try
        (#'plugins/load-plugin-configs!)
        (#'plugins/load-plugins!)
        ;; NB: error is thrown at load-time before plugin is used

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
      (#'plugins/load-plugin-configs!)
      (#'plugins/load-plugins!)

      (try
        (plugins/transform :test {})

        (catch clojure.lang.ExceptionInfo e
          (is (= {:type :sci/error
                  :line 7
                  :column 3
                  :message "hello from plugin"
                  :file nil}
                 (dissoc (ex-data e) :sci.impl/callstack))
              "the exception is wrapped fail and returned"))))))

(deftest test-process
  (with-open [server (stub/start! {"/" (fn [request]
                                         {:status 200
                                          :content-type "application/json"
                                          :body (json/generate-string {:success true
                                                                       :value (:value (:query-params request))
                                                                       :header (get-in request [:headers :x-a-header])})})})]

    (testing "with failing plugin"
      (with-redefs [env {:plugins [{:id :plugin/fail-process
                                    :filename "test-data/fail-process-plugin.md"}
                                   {:id :plugin/http-example-request
                                    :filename "test-data/example-request-plugin.md"
                                    :url (:uri server)}]
                         :extension-points {:test [:plugin/fail-process :plugin/http-example-request]}}]
        (#'plugins/load-plugin-configs!)
        (#'plugins/load-plugins!)

        (is (empty? (stub/recorded-responses server))
            (is (= [{:result :fail}]
                   (plugins/process :test {:value 42}))
                "the first process to return errors is where the execution stops (short-circuit evaluation)")))

      (testing "with process plugin"
        (with-redefs [env {:plugins [{:id :plugin/fail-process
                                      :filename "test-data/fail-process-plugin.md"} ; NB: declared but not used
                                     {:id :plugin/http-example-request
                                      :filename "test-data/example-request-plugin.md"
                                      :url (:uri server)}]
                           :extension-points {:test [:plugin/http-example-request]}}]

          (#'plugins/load-plugin-configs!)
          (#'plugins/load-plugins!)

          (plugins/process :test {:value 42})

          (is (= {:success true
                  :header "42"
                  :value "42"}
                 (json/parse-string (:body (first (stub/recorded-responses server)))))
              "HTTP request succeeds"))))))

(deftest test-validate
  (testing "with validate plugin"
    (with-redefs [env {:plugins [{:id :plugin/fail-odd
                                  :filename "test-data/fail-odd-plugin.md"}
                                 {:id :plugin/fail-neg
                                  :filename "test-data/fail-neg-plugin.md"}]
                       :extension-points {:test [:plugin/fail-odd :plugin/fail-neg]}}]
      (#'plugins/load-plugin-configs!)
      (#'plugins/load-plugins!)

      (testing "failing at runtime"
        (try
          (plugins/validate :test {})

          (catch clojure.lang.ExceptionInfo e
            (is (= {:type :sci/error
                    :line 7
                    :column 9
                    :message "Argument must be an integer: "
                    :file nil}
                   (dissoc (ex-data e) :sci.impl/callstack))
                "code will fail at runtime and the location is reported"))))

      (is (= nil (plugins/validate :test {:value 2}))
          "pass both validate")

      (is (= [{:result :fail :reason :odd}] (plugins/validate :test {:value 1}))
          "first validate")

      (is (= [{:result :fail :reason :neg}] (plugins/validate :test {:value -2}))
          "first validate is a pass but the second fails")

      (is (= [{:result :fail :reason :odd}]
             (plugins/validate :test {:value -1}))
          "the first validate to return errors is where the execution stops (short-circuit evaluation)"))))

