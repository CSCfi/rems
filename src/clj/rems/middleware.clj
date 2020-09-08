(ns rems.middleware
  (:require [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [clojure.pprint :refer [pprint]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [clojure.walk :refer [keywordize-keys]]
            [mount.core :as mount]
            [rems.auth.auth :as auth]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.organizations :as organizations]
            [rems.db.roles :as roles]
            [rems.db.user-settings :as user-settings]
            [rems.db.users :as users]
            [rems.env :refer [+defaults+]]
            [rems.layout :refer [error-page]]
            [rems.logging :refer [with-mdc]]
            [rems.util :refer [get-user-id getx-user-id update-present]]
            [ring-ttl-session.core :refer [ttl-memory-store]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.util.http-response :refer [unauthorized]]
            [ring.util.response :refer [redirect header]])
  (:import [javax.servlet ServletContext]
           [rems.auth ForbiddenException UnauthorizedException]))

(defn calculate-root-path [request]
  (if-let [context (:servlet-context request)]
    ;; If we're not inside a servlet environment
    ;; (for example when using mock requests), then
    ;; .getContextPath might not exist
    (try (.getContextPath ^ServletContext context)
         (catch IllegalArgumentException _ context))
    ;; if the context is not specified in the request
    ;; we check if one has been specified in the environment
    ;; instead
    (:app-context env)))

(defn- csrf-error-handler
  "CSRF error is typical when the user session is timed out
  and we wish to redirect to login in that case."
  [error]
  (unauthorized "Invalid anti-forgery token"))

(defn wrap-api-key-or-csrf-token
  "Custom wrapper for CSRF so that the API requests with valid `x-rems-api-key` don't need to provide CSRF token."
  [handler]
  (let [csrf-handler (wrap-anti-forgery handler {:error-handler csrf-error-handler})]
    (fn [request]
      (if (:uses-valid-api-key? request)
        (handler request)
        (csrf-handler request)))))

(defn- wrap-user
  "Binds context/*user* to the buddy identity."
  [handler]
  (fn [request]
    (binding [context/*user* (keywordize-keys (:identity request))]
      (when context/*user*
        (users/add-user-raw! (get-user-id) context/*user*))
      (with-mdc {:user (:eppn context/*user*)}
        (handler request)))))

(defn wrap-context [handler]
  (fn [request]
    (binding [context/*root-path* (calculate-root-path request)
              context/*roles* (set/union
                               (when context/*user*
                                 (set/union (roles/get-roles (getx-user-id))
                                            (organizations/get-all-organization-roles (getx-user-id))
                                            (applications/get-all-application-roles (getx-user-id))))
                               (when (:uses-valid-api-key? request)
                                 #{:api-key}))]
      (with-mdc {:roles (str/join " " (sort context/*roles*))}
        (handler request)))))

(defn wrap-role-headers [handler]
  (fn [request]
    (cond-> (handler request)
      (not (empty? context/*roles*)) (header "x-rems-roles" (str/join " " (sort (map name context/*roles*)))))))

(deftest test-wrap-role-headers
  (testing "no roles"
    (is (= {}
           (binding [context/*roles* nil]
             ((wrap-role-headers identity) {}))))
    (is (= {}
           (binding [context/*roles* #{}]
             ((wrap-role-headers identity) {})))))
  (testing "one role"
    (is (= {:headers {"x-rems-roles" "foo"}}
           (binding [context/*roles* #{:foo}]
             ((wrap-role-headers identity) {})))))
  (testing "multiple role"
    (is (= {:headers {"x-rems-roles" "bar foo"}}
           (binding [context/*roles* #{:foo :bar}]
             ((wrap-role-headers identity) {}))))))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t "Internal error" (with-out-str (when-let [data (ex-data t)]
                                                      (pprint data))))
        (error-page {:status 500
                     :title "System error occurred!"
                     :message "We are working on fixing the issue."})))))

(defn on-restricted-page [request response]
  (assoc (redirect "/login")
         :session (assoc (:session response) :redirect-to (:uri request))))

(defn wrap-restricted
  [handler]
  (restrict handler {:handler authenticated?
                     :on-error on-restricted-page}))

(defn wrap-i18n
  "Sets context/*lang*"
  [handler]
  (fn [request]
    (binding [context/*lang* (or (when context/*user*
                                   (:language (user-settings/get-user-settings (getx-user-id))))
                                 (:default-language env))]
      (handler request))))

(defn on-unauthorized-error [request]
  (error-page
   {:status 401
    :title (str "Access to " (:uri request) " is not authorized")}))

(defn on-forbidden-error [request]
  (error-page
   {:status 403
    :title (str "Access to " (:uri request) " is forbidden")}))

(defn wrap-unauthorized-and-forbidden
  "Handles unauthorized exceptions by showing an error page."
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch UnauthorizedException e
        (on-unauthorized-error req))
      (catch ForbiddenException e
        (on-forbidden-error req)))))

(defn wrap-logging
  [handler]
  (fn [request]
    (let [uri (str (:uri request)
                   (when-let [q (:query-string request)]
                     (str "?" q)))]
      (log/info ">" (:request-method request) uri
                "lang:" context/*lang*
                "user:" context/*user*
                (if (:uses-valid-api-key? request)
                  "api-key"
                  "")
                "roles:" context/*roles*)
      (log/debug "session" (pr-str (:session request)))
      (when (seq (:form-params request))
        (log/debug "form params" (pr-str (:form-params request))))
      (let [response (handler request)]
        (log/info "<" (:request-method request) uri (:status response)
                  (or (get-in response [:headers "Location"]) ""))
        response))))

(defn- wrap-request-context [handler]
  (fn [request]
    (with-mdc {:request-method (str/upper-case (name (:request-method request)))
               :request-uri (:uri request)}
      (handler request))))

(defn- unrelativize-url [url]
  (if (.startsWith url "/")
    (str (:public-url env) (.substring url 1))
    url))

(deftest test-unrelativize-url
  (with-redefs [env {:public-url "http://public.url/"}]
    (is (= "http://public.url/" (unrelativize-url "/")))
    (is (= "http://public.url/foo/bar" (unrelativize-url "/foo/bar")))
    (is (= "http://example.com/foo/bar" (unrelativize-url "http://example.com/foo/bar")))))

(defn- wrap-fix-location-header
  "When we try to redirect to a relative url, prefix the url with (:public-url env).
   If we don't do this, Jetty does it for us but uses the request url instead of :public-url."
  [handler]
  (fn [request]
    (-> request
        handler
        (update :headers update-present "Location" unrelativize-url))))

(mount/defstate session-store
  :start (ttl-memory-store (* 60 30)))

(defn get-active-users []
  ;; We're poking into the internals of ring-ttl-session.core. Would
  ;; be neater to implement our own introspectable session store.
  (doall
   (for [session (vals (.em_map session-store))
         :let [identity (:identity session)]
         :when identity]
     (users/format-user identity))))


(defn wrap-defaults-settings []
  (-> site-defaults
      (assoc-in [:security :anti-forgery] false)
      (assoc-in [:session :store] session-store)
      (assoc-in [:session :flash] true)
      ; ring-defaults sets the cookies with strict same-site limits, but this breaks OpenID Connect logins.
      ; Different options for using lax cookies are described in the authentication ADR.
      (assoc-in [:session :cookie-attrs] {:http-only true, :same-site :lax})))

(defn wrap-base [handler]
  (-> ((:middleware +defaults+) handler)
      wrap-fix-location-header
      wrap-unauthorized-and-forbidden
      wrap-logging
      wrap-i18n
      wrap-role-headers
      wrap-context
      wrap-user
      wrap-api-key-or-csrf-token
      auth/wrap-auth
      wrap-webjars ;; serves our webjar (https://www.webjars.org/) dependencies as /assets/<webjar>/<file>
      (wrap-defaults (wrap-defaults-settings))
      wrap-internal-error
      wrap-request-context))
