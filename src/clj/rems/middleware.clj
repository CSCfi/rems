(ns rems.middleware
  (:require [clojure.pprint :refer [pprint]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [clojure.walk :refer [keywordize-keys]]
            [medley.core :refer [update-existing]]
            [mount.core :as mount]
            [nano-id.core :as nano-id]
            [rems.auth.auth :as auth]
            [rems.auth.util :refer [throw-forbidden throw-unauthorized]]
            [rems.common.util :refer [assoc-some-in]]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.organizations :as organizations]
            [rems.db.roles :as roles]
            [rems.db.user-settings :as user-settings]
            [rems.db.workflow :as workflow]
            [rems.layout :refer [error-page]]
            [rems.logging :refer [with-mdc]]
            [rems.middleware.dev :refer [wrap-dev]]
            [rems.multipart]
            [rems.util :refer [getx-user-id]]
            [ring-ttl-session.core :refer [ttl-memory-store]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.util.http-response :refer [unauthorized]]
            [ring.util.response :refer [bad-request redirect header]])
  (:import [javax.servlet ServletContext]
           [rems.auth ForbiddenException UnauthorizedException]))

(def nano-id (nano-id/custom "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz" 8))

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
      (with-mdc {:user (:userid context/*user*)}
        (handler request)))))

(defn wrap-context [handler]
  (fn [request]
    (binding [context/*request* (assoc request :request-id (random-uuid))
              context/*root-path* (calculate-root-path request)
              context/*roles* (set/union
                               (when context/*user*
                                 (set/union (roles/get-roles (getx-user-id))
                                            (organizations/get-all-organization-roles (getx-user-id))
                                            (workflow/get-all-workflow-roles (getx-user-id))
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
      (catch clojure.lang.ExceptionInfo e
        (if (auth/get-api-key req) ; not our web app
          ;; straight error
          (bad-request (.getMessage e))

          ;; redirect browser to an error page
          (let [data (ex-data e)
                url (str "/error?key="
                         (:key data)
                         (apply str (for [arg (:args data)]
                                      (str "&args[]=" arg))))]
            (log/error e "Error" (with-out-str (some-> data pprint)))
            (redirect url))))
      (catch Throwable t
        (log/error t "Internal error" (with-out-str (when-let [data (ex-data t)]
                                                      (pprint data))))
        (error-page {:status 500
                     :title "System error occurred!"
                     :message "We are working on fixing the issue."})))))

(defn wrap-i18n
  "Sets context/*lang*"
  [handler]
  (fn [request]
    (let [user-specified-language (or (some-> request :cookies (get "rems-user-preferred-language") :value keyword)
                                      (when context/*user*
                                        (:language (user-settings/get-user-settings (getx-user-id)))))]
      (binding [context/*lang* (or user-specified-language
                                   (:default-language env))]
        (let [response (handler request)]
          ;; ensure the cookie is set for future requests
          (if user-specified-language
            (assoc-some-in response [:cookies "rems-user-preferred-language"] (some-> context/*lang* name))
            response))))))

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

;; When using devtools, the browser fetches source maps etc.
;; We filter them out to keep the log cleaner.
;; This helps e.g. when debugging browser test failures.
(def silence-logging-regex #"^/js/cljs-runtime/.*")

(defn wrap-context-logging
  "Logs detailed information about a request just before
  the main business code is run.

  Here we have the most context about the user and request available."
  [handler]
  (fn [request]
    (let [uri (str (:uri request)
                   (when-let [q (:query-string request)]
                     (str "?" q)))
          log? (not (re-matches silence-logging-regex uri))]
      (when log?
        (log/info ">" (:request-method request) uri
                  "lang:" context/*lang*
                  "user:" context/*user*
                  (if (:uses-valid-api-key? request)
                    "api-key"
                    "")
                  "roles:" context/*roles*)

        (log/debug "session" (pr-str (:session request)))

        (when (seq (:form-params request))
          (log/debug "form params" (pr-str (:form-params request)))))

      (handler request))))

(def request-count (atom 0))

(defn wrap-entry-logging
  "Logs every request into an entry `req >` and exit `req <` log.

  This happens in the very beginning of the request processing, so
  not a lot of context is available yet.

  This is important to show a request as soon as it's possible, so if it
  starts to wait on middleware DB requests or such, it still appears
  in the logs."
  [handler]
  (fn [request]
    (let [uri (str (:uri request)
                   (when-let [q (:query-string request)]
                     (str "?" q)))
          start (System/nanoTime)
          log? (not (re-matches silence-logging-regex uri))
          userid (or (get-in request [:session :identity :userid])
                     (:userid context/*user*)
                     (auth/get-api-userid request))]
      (try
        (swap! request-count inc)

        (with-mdc {:userid userid
                   :request-count @request-count}
          (when log?
            (log/info "req >" (:request-method request) uri)
            (when (seq (:form-params request))
              (log/debug "form params" (pr-str (:form-params request)))))

          (let [response (handler request)]
            (when log?
              (log/info "req <" (:request-method request) uri (:status response)
                        (str (int (/ (- (System/nanoTime) start) 1000000)) "ms")
                        (or (get-in response [:headers "Location"]) "")))
            response))
        (finally
          (swap! request-count dec))))))

(defn- wrap-request-context [handler]
  (fn [request]
    (let [request-id (nano-id)] ; short id for separating requests
      (binding [context/*request* (assoc request :request-id request-id)]
        (with-mdc {:request-id request-id
                   :request-method (str/upper-case (name (:request-method request)))
                   :request-uri (:uri request)}
          (handler request))))))

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
        (update :headers update-existing "Location" unrelativize-url))))

(mount/defstate session-store
  :start (ttl-memory-store (* 60 30)))

(defn get-active-users []
  ;; We're poking into the internals of ring-ttl-session.core. Would
  ;; be neater to implement our own introspectable session store.
  (doall
   (for [session (vals (.em_map session-store))
         :let [identity (:identity session)]
         :when identity]
     identity)))

(defn get-session
  "Returns the session associated with a cookie. Useful for testing."
  [cookie]
  (let [[_k v] (str/split cookie #"=")] ; ring-session=...
    (.read-session session-store v)))

(defn wrap-cache-control
  "In case a Cache-Control header is missing, add a default of no-store"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (when response
        (update response :headers (partial merge {"Cache-Control" "no-store"}))))))

(defn wrap-cacheable
  "Set a Cache-Control max-age of 23h to mark the response as cacheable."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (when response
        (header response "Cache-Control" (str "max-age=" (* 60 60 23)))))))

(defn wrap-defaults-settings []
  (-> site-defaults
      (dissoc :static) ;; we handle serving static resources in rems.handler
      (assoc-in [:security :anti-forgery] false)
      (assoc-in [:session :store] session-store)
      (assoc-in [:session :flash] true)
      (assoc-in [:params :multipart] {:store (rems.multipart/size-limiting-temp-file-store {:max-size (:attachment-max-size env) :expires-in 3600})})
      ;; ring-defaults sets the cookies with strict same-site limits, but this breaks OpenID Connect logins.
      ;; Different options for using lax cookies are described in the authentication ADR.
      (assoc-in [:session :cookie-attrs] {:http-only true, :same-site :lax})))

(defn wrap-base [handler]
  (-> handler
      ((if (:dev env) wrap-dev identity))
      wrap-fix-location-header
      wrap-unauthorized-and-forbidden
      wrap-context-logging
      wrap-i18n
      wrap-role-headers
      wrap-context
      wrap-user
      wrap-api-key-or-csrf-token
      auth/wrap-auth
      wrap-entry-logging
      (wrap-defaults (wrap-defaults-settings))
      wrap-cache-control
      wrap-internal-error
      wrap-request-context))
