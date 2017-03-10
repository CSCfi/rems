(ns rems.test.handler
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [ring.mock.request :refer :all]
            [rems.handler :refer :all]
            [mount.core :as mount]
            [rems.db.core :as db]
            [rems.env :refer [*db*]]
            [luminus-migrations.core :as migrations]
            [rems.config :refer [env]]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'rems.config/env
      #'rems.env/*db*
      #'rems.db.core/catalogue-item-localizations)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (db/create-test-data!)
    (f)
    (mount/stop)))

(defn pass-cookies [to-request from-response]
  (let [set-cookie (get-in from-response [:headers "Set-Cookie"])]
    (assoc-in to-request [:headers "cookie"] (s/join "; " set-cookie))))

(defn get-csrf-token [response]
  (let [token-regex #"<input id=\"__anti-forgery-token\" name=\"__anti-forgery-token\" type=\"hidden\" value=\"([^\"]*)\">"
        [_ token] (re-find token-regex (:body response))]
    token))

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

(deftest test-csrf
  (testing "cart routes"
    (testing "without CSRF token"
      (let [response (app (request :post "/cart/add"))]
        (is (= 403 (:status response)))
        (is (.contains (:body response) "anti-forgery"))
        (is (nil? (:session response)))))
    (testing "with CSRF token"
      (let [;; no real mechanism for mocking the token or the session,
            ;; so we log in, get the catalogue, etc.
            login (app (request :get "/Shibboleth.sso/Login"))
            catalogue (app (-> (request :get "/catalogue")
                               (pass-cookies login)))
            token (get-csrf-token catalogue)
            req (-> (request :post "/cart/add")
                    (pass-cookies login)
                    (assoc :form-params {"item" "A" "__anti-forgery-token" token}))
            response (app req)]
        (is (= 303 (:status response)))))))

(deftest test-language-switch
  (let [login (app (request :get "/Shibboleth.sso/Login"))
        catalogue (app (-> (request :get "/catalogue")
                           (pass-cookies login)))]
    (is (.contains (:body catalogue) "cart") "defaults to english")
    (let [token (get-csrf-token catalogue)
          fi (app (-> (request :post "/language/fi")
                      (pass-cookies login)
                      (assoc :form-params {"__anti-forgery-token" token})
                      (assoc-in [:headers "referer"] "/catalogue")))
          catalogue-fi (app (-> (request :get "/catalogue")
                                (pass-cookies login)))]
      (is (= 303 (:status fi)))
      (is (.endsWith (get-in fi [:headers "Location"]) "/catalogue")
          "language switch redirects back")
      (is (.contains (:body catalogue-fi) "kori")
          "language switches to finnish"))))
