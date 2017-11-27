(ns ^:integration rems.test.handler
  (:require [clojure.string :as s]
            [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [hickory.core :as h]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.db.test-data :as test-data]
            [rems.handler :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [ring.mock.request :refer :all]
            [ring.util.codec :refer [form-decode]]))

(use-fixtures
  :once
  fake-tempura-fixture
  (fn [f]
    (mount/start
      #'rems.config/env
      #'rems.env/*db*
      #'rems.handler/app)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (test-data/create-test-data!)
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




;;; context language

(defn new-context
  "Create a new test context that tracks visited pages, passes cookies
  and the CSRF token, when sending further requests by using dispatch.

  You must give your application's handler, which will be stored and
  subsequently used in the request dispatching.

  See also: dispatch, login, follow-redirect"
  [app] {:app app})

(defn dispatch
  "Send a single request in the given context returning a new context.

  Requests are like the regular ring requests that can be created with
  the standard tools.

  The context object has the :response and :status available.

  Example:

    (dispatch (request :get \"/resource\" {:id 1}))"
  [{:keys [app cookie csrf-token requests response] :as ctx :or {requests []}} req]
  (let [req (if cookie
              (header req "cookie" (s/join "; " cookie))
              req)
        send-token? (and (= :post (:request-method req)) csrf-token)
        req (if send-token?
              (body req (merge {"__anti-forgery-token" csrf-token}
                               (when (:body req) (form-decode (slurp (:body req))))))
              req)]
    (let [response (app req)
          set-cookie (get-in response [:headers "Set-Cookie"])
          request-entry [(:request-method req)
                         (:uri req)
                         (remove nil? [(when cookie :send-cookie)
                                       (when send-token? :send-csrf-token)])
                         :=>
                         (:status response)
                         (remove nil? [(when set-cookie :set-cookie)])]]
      (merge ctx
             {:cookie (or set-cookie cookie)
              :csrf-token (get-csrf-token response)
              :status (:status response)
              :requests (conj requests request-entry)
              :response response}
             ))))

(defn login
  "Logs in the given user by sending a request to the fake login.

  You can give desired roles as parameters.

  The user is created in the DB as well as given the roles."
  [ctx username & roles]
  (db/add-user! {:user username :userattrs  nil})
  (doseq [role roles]
    (roles/add-role! username role))
  (dispatch ctx (-> (request :get "/Shibboleth.sso/Login" {:username username}))))

(defn follow-redirect
  "Ensures the previous response in the context was a redirect and
  dispatches a GET to the address in the Location header."
  [ctx]
  (assert (contains? #{302 303} (:status ctx)) (str "Not redirect " (:status ctx) ""))
  (let [location (get-in (:response ctx) [:headers "Location"])
        location (subs location 16)]
    (dispatch ctx (request :get location))))

(defn ctx->html
  "Takes the last response in the given context and turns it into a Hiccup data-structure."
  [ctx]
  (-> ctx (:response) (:body) (h/parse) (h/as-hiccup) (second)))




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
      (let [login-ctx (login (new-context app) "bob")]
        (is (= 302 (:status login-ctx)) "should return redirect")
        (is (= "http://localhost/landing_page"
               (get-in login-ctx [:response :headers "Location"]))
            "login should redirect to /landing_page")
        (testing "after landing page"
          (let [landing-ctx (follow-redirect login-ctx)]
            (testing "successfully"
              (let [catalogue-ctx (follow-redirect landing-ctx)]
                (is (= 200 (:status catalogue-ctx)) "should return 200 OK")))))
        (testing "redirect logged in users accessing root context"
          (let [home-ctx (dispatch
                           (follow-redirect login-ctx)
                           (request :get "/"))]
            (is (= 302 (:status home-ctx)))
            (is (= "http://localhost/landing_page"
                   (get-in home-ctx [:response :headers "Location"]))))))))

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
            response (-> (new-context app)
                         (login "jack")
                         (dispatch (request :get "/catalogue"))
                         ;; csrf token added automatically by dispatch
                         (dispatch (request :post "/cart/add" {"id" "1"}))
                         :response)]
        (is (= 303 (:status response)))))))



(deftest test-language-switch
  (let [ctx (-> (new-context app)
                (login "john")
                (dispatch (request :get "/catalogue")))]
    (is (.contains (get-in ctx [:response :body]) "cart") "defaults to english")
    (let [fi-ctx (dispatch ctx (header (request :post "/language/fi")
                                       "referer" "/catalogue"))
          catalogue-ctx (follow-redirect fi-ctx)]
      (is (= 303 (:status fi-ctx)))
      (is (.endsWith (get-in fi-ctx [:response :headers "Location"]) "/catalogue")
          "language switch redirects back")
      (is (.contains (get-in catalogue-ctx [:response :body]) "kori")
          "language switches to finnish"))))



(deftest test-authz
  (testing "when jack makes an application"
    (-> (new-context app)
        (login "jack")
        (follow-redirect)
        (follow-redirect)
        (dispatch (request :post "/cart/add" {"id" "1"}))
        (follow-redirect)
        (dispatch (request :get "/form?catalogue-items=1"))
        (follow-redirect)
        (dispatch (request :post "/form/-1/save" {"field2" "jack field2"}))
        (follow-redirect))
    (testing "and jill goes to view applications"
      (let [application (:id (first (db/get-applications {:applicant "jack"})))
            ctx (-> (new-context app)
                    (login "jill")
                    (follow-redirect)
                    (dispatch (request :get "/applications")))]
        (is (empty? (hiccup-find [:.application] (ctx->html ctx))) "jill shouldn't see jack's application")
        (testing "jill tries to open jack's application"
          (let [url (format "/form/%s" application)
                ctx (dispatch ctx (request :get url))]
            (is (= 403 (:status ctx)) "jill shouldn't be authorized")))
        (testing "jill should still be able to make her own application"
          (let [ctx (dispatch ctx (request :get "/form?catalogue-items=1"))
                ctx (follow-redirect ctx)]
            (is (= 200 (:status ctx)))))
        (testing "jill tries to write to jack's application"
          (let [url (format "/form/%s/save" application)
                ctx (dispatch ctx (request :post url {"field2" "jill field2"}))]
            (is (= 403 (:status ctx)) "jill shouldn't be authorized")))))))

(deftest test-roles
  (testing "when applicant logs in"
    (let [ctx (-> (new-context app)
                  (login "test-roles-applicant" :applicant)
                  (follow-redirect)
                  (follow-redirect))]
      (is (not-empty (hiccup-find [:.catalogue] (ctx->html ctx))) "applicant sees catalogue initially")
      (is (contains? (set (db/get-users)) {:userid "test-roles-applicant"}))))
  (testing "when approver logs in"
    (let [ctx (-> (new-context app)
                  (login "test-roles-approver" :approver)
                  (follow-redirect)
                  (follow-redirect))]
      (is (not-empty (hiccup-find [:.actions] (ctx->html ctx))) "approver sees approvals initially")
      (is (contains? (set (db/get-users)) {:userid "test-roles-approver"}))
      (is (not-empty (hiccup-find [:#handled-approvals] (ctx->html ctx))) "approver sees handled approvals initially")))
  (testing "when reviewer logs in"
    (let [ctx (-> (new-context app)
                  (login "test-roles-reviewer" :reviewer)
                  (follow-redirect)
                  (follow-redirect))]
      (is (not-empty (hiccup-find [:.actions] (ctx->html ctx))) "reviewer sees reviews initially")
      (is (contains? (set (db/get-users)) {:userid "test-roles-reviewer"}))
      (is (not-empty (hiccup-find [:#handled-reviews] (ctx->html ctx))) "reviewer sees handled reviews initially"))))
