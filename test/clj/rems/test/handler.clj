(ns ^:integration rems.test.handler
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [hickory.core :as h]
            [ring.mock.request :refer :all]
            [ring.util.codec :refer [form-decode]]
            [hiccup-find.core :refer :all]
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
      #'rems.env/*db*)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (db/create-test-data!)
    (f)
    (mount/stop)))

;;; test helpers

(defn- pass-cookies [to-request from-response]
  (let [set-cookie (get-in from-response [:headers "Set-Cookie"])]
    (header to-request "cookie" (s/join "; " set-cookie))))

(defn- get-csrf-token [response]
  (let [token-regex #"<input id=\"__anti-forgery-token\" name=\"__anti-forgery-token\" type=\"hidden\" value=\"([^\"]*)\">"
        [_ token] (re-find token-regex (:body response))]
    token))

(defn new-context [app] {:app app})

(defn ctx->html [ctx]
  (-> ctx (:response) (:body) (h/parse) (h/as-hiccup) (second)))

(defn dispatch [{:keys [app cookie csrf-token response] :as ctx} req]
  (let [req (if cookie
              (header req "cookie" (s/join "; " cookie))
              req)
        send-token? (and (= :post (:request-method req)) csrf-token)
        req (if send-token?
              (body req (merge {"__anti-forgery-token" csrf-token}
                               (when (:body req) (form-decode (slurp (:body req))))))
              req)]
    (let [response (app req)
          set-cookie (get-in response [:headers "Set-Cookie"])]
      (println (:request-method req)
               (:uri req)
               (remove nil? [(when cookie :send-cookie)
                             (when send-token? :send-csrf-token)])
               "=>"
               (:status response)
               (remove nil? [(when set-cookie :set-cookie)]))
      (merge ctx
             {:cookie (or set-cookie cookie)
              :csrf-token (get-csrf-token response)
              :status (:status response)
              :response response}
             ))))

(defn login [ctx username]
  (dispatch ctx (-> (request :get "/Shibboleth.sso/Login")
                    (assoc :fake-username username))))

(defn follow-redirect [ctx]
  (assert (contains? #{302 303} (:status ctx)) (str "Not redirect " (:status ctx) ""))
  (let [location (get-in (:response ctx) [:headers "Location"])
        location (subs location 16)]
    (dispatch ctx (request :get location))))

(defn print-inputs [ctx]
  (let [html (ctx->html ctx)]
    (clojure.pprint/pprint (hiccup-find [:input] html)))
  ctx)




;;; tests

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
            req (-> (request :post "/cart/add" {"id" "1" "__anti-forgery-token" token})
                    (pass-cookies login))
            response (app req)]
        (is (= 303 (:status response)))))))



(deftest test-language-switch
  (let [login (app (request :get "/Shibboleth.sso/Login"))
        catalogue (app (-> (request :get "/catalogue")
                           (pass-cookies login)))]
    (is (.contains (:body catalogue) "cart") "defaults to english")
    (let [token (get-csrf-token catalogue)
          fi (app (-> (request :post "/language/fi" {"__anti-forgery-token" token})
                      (pass-cookies login)
                      (header "referer" "/catalogue")))
          catalogue-fi (app (-> (request :get "/catalogue")
                                (pass-cookies login)))]
      (is (= 303 (:status fi)))
      (is (.endsWith (get-in fi [:headers "Location"]) "/catalogue")
          "language switch redirects back")
      (is (.contains (:body catalogue-fi) "kori")
          "language switches to finnish"))))



(deftest test-authz
  (testing "when one user makes an application"
    (-> (new-context app)
        (login "alice")
        (follow-redirect)
        (dispatch (request :post "/cart/add" {"id" "1"}))
        (follow-redirect)
        (dispatch (request :get "/form/1"))
        (dispatch (request :post "/form/1/save" {"field2" "alice field2"}))
        (follow-redirect))
    (testing "and another user goes to the same application"
      (let [html (-> (new-context app)
                     (login "bob")
                     (follow-redirect)
                     (dispatch (request :get "/form/1/1"))
                     ctx->html)]
        (is (not= "alice field2" (:value (hiccup-attrs (first (hiccup-find [:input {:name "field2"}] html)))))
            "bob shouldn't see alice's applications")))))
