(ns rems.auth.oidc
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET defroutes]]
            [medley.core :refer [find-first]]
            [rems.config :refer [env oidc-configuration]]
            [rems.db.user-mappings :as user-mappings]
            [rems.db.users :as users]
            [rems.ga4gh :as ga4gh]
            [rems.json :as json]
            [rems.jwt :as jwt]
            [rems.util :refer [getx]]
            [ring.util.response :refer [redirect]])
  (:import [java.time Instant]))

(defn login-url []
  (str (:authorization_endpoint oidc-configuration)
       "?response_type=code"
       "&client_id=" (getx env :oidc-client-id)
       "&redirect_uri=" (getx env :public-url) "oidc-callback"
       "&scope=" (getx env :oidc-scopes)
       (getx env :oidc-additional-authorization-parameters)
       #_"&state=STATE")) ; FIXME We could use the state for intelligent redirect. Also check if we need it for CSRF protection as Auth0 docs say.

(defn logout-url []
  "/oidc-logout")

(defn- get-userid-attributes [user-data]
  (for [{:keys [attribute rename]} (getx env :oidc-userid-attributes)
        :let [value (get user-data (keyword attribute))]
        :when value]
    [(or rename attribute) value]))

(deftest test-get-userid-attributes
  (with-redefs [env {:oidc-userid-attributes [{:attribute "sub" :rename "elixirId"}
                                              {:attribute "old_sub"}]}]
    (is (= [] (get-userid-attributes nil)))
    (is (= [["elixirId" "elixir-alice"]
            ["old_sub" "alice"]]
           (get-userid-attributes {:old_sub "alice"
                                   :sub "elixir-alice"
                                   :name "Alice Applicant"})))))

(defn- get-new-userid
  "Returns a new userid for a user based on the given `user-data` identity data.

  The userid will actually be the first valid attribute value from the identity data."
  [user-data]
  (first (keep second (get-userid-attributes user-data))))

(deftest test-get-new-userid
  (with-redefs [env {:oidc-userid-attributes [{:attribute "atr"}]}]
    (is (nil? (get-new-userid {:sub "123"})))
    (is (= "456" (get-new-userid {:sub "123" :atr "456"}))))

  (with-redefs [env {:oidc-userid-attributes [{:attribute "atr"}
                                              {:attribute "sub"}
                                              {:attribute "fallback"}]}]
    (is (= "123" (get-new-userid {:sub "123"})))
    (is (= "456" (get-new-userid {:sub "123" :atr "456"})))
    (is (= "456" (get-new-userid {:sub "123" :atr "456" :fallback "78"})))
    (is (= "78" (get-new-userid {:fallback "78"}))))

  (with-redefs [env {:oidc-userid-attributes [{:attribute "atr" :rename "elixirId"}
                                              {:attribute "sub"}]}]
    (is (= "elixir-alice" (get-new-userid {:atr "elixir-alice" :sub "123"})))
    (is (= "123" (get-new-userid {:elixirId "elixir-alice" :sub "123"}))
        "user data should use names before rename")))

(defn save-user-mappings! [user-data userid]
  (let [attrs (get-userid-attributes user-data)]
    (doseq [[attr value] attrs
            :when (not= value userid)]
      (user-mappings/create-user-mapping! {:userid userid
                                           :ext-id-attribute attr
                                           :ext-id-value value}))))

(defn- find-user [user-data]
  (let [userid-attrs (get-userid-attributes user-data)
        user-mapping-match (fn [[attribute value]]
                             (let [mappings (user-mappings/get-user-mappings {:ext-id-attribute attribute :ext-id-value value})]
                               (:userid (first mappings))))] ; should be at most one by kv search
    (or (some user-mapping-match userid-attrs)
        (find-first users/user-exists? (map second userid-attrs)))))

(defn- upsert-user! [user]
  (let [userid (:userid user)]
    (users/add-user-raw! userid user)
    user))

(defn- get-user-attributes [user-data]
  ;; TODO all attributes could support :rename
  (let [userid (or (find-user user-data) (get-new-userid user-data))
        _ (assert userid (when (:log-authentication-details env) {:user-data user-data}))
        identity-base {:userid userid
                       :name (some (comp user-data keyword) (:oidc-name-attributes env))
                       :email (some (comp user-data keyword) (:oidc-email-attributes env))}
        extra-attributes (select-keys user-data (map (comp keyword :attribute) (:oidc-extra-attributes env)))
        user-info-attributes (select-keys user-data [:researcher-status-by])]
    (merge identity-base extra-attributes user-info-attributes)))

;; XXX: consider joining with rems.db.users/invalid-user?
(defn- validate-user! [user]
  ;; userid already checked
  (when-let [errors (seq (remove nil?
                                 [(when (and (:oidc-require-name env) (str/blank? (:name user))) :t.login.errors/name)
                                  (when (and (:oidc-require-email env) (str/blank? (:email user))) :t.login.errors/email)]))]
    (throw (ex-info "Invalid user"
                    {:key :t.login.errors/invalid-user
                     :args errors
                     :user user}))))

(defn find-or-create-user! [user-data]
  (let [user (get-user-attributes user-data)
        _ (validate-user! user)
        user (upsert-user! user)]
    (save-user-mappings! user-data (:userid user))
    user))

(defn oidc-callback [request]
  (let [error (get-in request [:params :error])
        code (get-in request [:params :code])]
    (cond error
          (do (log/warn "Login error in oidc-callback" (pr-str error))
              (redirect "/error?key=:t.login.errors/unknown"))

          (str/blank? code)
          (do (log/warn "Missing code in oidc-callback" (pr-str code))
              (redirect "/error?key=:t.login.errors/unknown"))

          :else
          (let [response (-> (http/post (:token_endpoint oidc-configuration)
                                        ;; NOTE Some IdPs don't support client id and secret in form params,
                                        ;;      and require us to use HTTP basic auth
                                        {:basic-auth [(str (getx env :oidc-client-id))
                                                      (getx env :oidc-client-secret)]
                                         :form-params {:grant_type "authorization_code"
                                                       :code code
                                                       :redirect_uri (str (getx env :public-url) "oidc-callback")}
                                         ;; Setting these will cause the exceptions raised by http/post to contain
                                         ;; the request body, useful for debugging failures.
                                         :save-request? (getx env :log-authentication-details)
                                         :debug-body (getx env :log-authentication-details)})
                             ;; FIXME Complains about Invalid cookie header in logs
                             ;; TODO Unhandled responses for token endpoint:
                             ;;      403 {\"error\":\"invalid_grant\",\"error_description\":\"Invalid authorization code\"} when reusing codes
                             (:body)
                             (json/parse-string))
                access-token (:access_token response)
                id-token (:id_token response)
                issuer (:issuer oidc-configuration)
                audience (getx env :oidc-client-id)
                now (Instant/now)
                ;; id-data has keys:
                ;; sub – unique ID
                ;; name - non-unique name
                ;; locale – could be used to set preferred lang on first login
                ;; email – non-unique (!) email
                id-data (jwt/validate id-token issuer audience now)
                user-info (when-let [url (:userinfo_endpoint oidc-configuration)]
                            (-> (http/get url {:headers {"Authorization" (str "Bearer " access-token)}})
                                :body
                                json/parse-string))
                researcher-status (ga4gh/passport->researcher-status-by user-info)
                user-data (merge id-data user-info researcher-status)
                user (find-or-create-user! user-data)]
            (when (:log-authentication-details env)
              (log/info "logged in" user-data user))
            (-> (redirect "/redirect")
                (assoc :session (:session request))
                (assoc-in [:session :access-token] access-token)
                (assoc-in [:session :identity] user))))))

(defn- oidc-revoke [token]
  (when token
    (let [endpoint (:revocation_endpoint oidc-configuration)]
      (when (:log-authentication-details env)
        (log/info "revoking token" endpoint))

      (when endpoint
        (let [response (http/post endpoint
                                  {:basic-auth [(getx env :oidc-client-id)
                                                (getx env :oidc-client-secret)]
                                   :form-params {:token token}
                                   :throw-exceptions false})]
          (when (:log-authentication-details env)
            (log/info "performed revocation" (:status response)))

          (when-not (= 200 (:status response))
            (log/error "received HTTP status" (:status response) "from" endpoint)))))))

(defroutes routes
  (GET "/oidc-login" _req (redirect (login-url)))
  (GET "/oidc-logout" req
    (let [session (get req :session)
          redirect-url (:oidc-logout-redirect-url env)]
      (when (:log-authentication-details env)
        (log/info "logging out" (:identity session)))

      (when (:oidc-perform-revoke-in-logout env)
        (oidc-revoke (:access-token session)))

      (when (:log-authentication-details env)
        (log/info "redirecting to" redirect-url))

      (assoc (redirect redirect-url)
             :session (dissoc session :identity :access-token))))
  (GET "/oidc-callback" req (oidc-callback req)))

