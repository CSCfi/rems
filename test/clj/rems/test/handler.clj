(ns rems.test.handler
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [ring.mock.request :refer :all]
            [rems.handler :refer :all]
            [mount.core :as mount]
            [rems.db.core :as db]
            [rems.env :refer [*db*]]
            [luminus-migrations.core :as migrations]
            [rems.config :refer [env]]
))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'rems.config/env
      #'rems.env/*db*)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (f)
    (mount/stop)))

(defn pass-cookies [to-request from-response]
  (let [set-cookie (get-in from-response [:headers "Set-Cookie"])]
    (assoc-in to-request [:headers "cookie"] (s/join "; " set-cookie))))

(deftest test-app
  (testing "main route"
    (let [response (app (request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "catalogue route"
    (testing "when unauthorized"
      (let [response (app (request :get "/catalogue"))]
        (is (= 403 (:status response)) "should return 403 unauthorized)")))

    (testing "when logging in"
      (let [login-response (app (request :get "/Shibboleth.sso/Login"))]
        (is (= 302 (:status login-response)) "should return redirect")
        (is (= "http://localhost/catalogue" (get-in login-response [:headers "Location"])) "login should redirect to /catalogue")
        (testing "successfully"
          (let [catalogue-request (-> (request :get "/catalogue") (pass-cookies login-response))
                catalogue-response (app catalogue-request)]
            (is (= 200 (:status catalogue-response)) "should return 200 OK"))))))

  (testing "not-found route"
    (let [response (app (request :get "/invalid"))]
      (is (= 404 (:status response))))))
