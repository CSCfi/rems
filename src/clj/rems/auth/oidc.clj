(ns rems.auth.oidc
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET defroutes]]
            [rems.config :refer [env oidc-configuration]]
            [rems.db.user-mappings :as user-mappings]
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

(defn get-userid [id-data]
  (let [attrs (->> (getx env :oidc-userid-attributes)
                   (map (comp keyword :attribute)))]
    (some (partial get id-data) attrs)))

(deftest test-get-userid
  (with-redefs [env {:oidc-userid-attributes [{:attribute "atr"}]}]
    (is (nil? (get-userid {:sub "123"})))
    (is (= "456" (get-userid {:sub "123" :atr "456"}))))
  (with-redefs [env {:oidc-userid-attributes [{:attribute "atr"}
                                              {:attribute "sub"}
                                              {:attribute "fallback"}]}]
    (is (= "123" (get-userid {:sub "123"})))
    (is (= "456" (get-userid {:sub "123" :atr "456"})))
    (is (= "456" (get-userid {:sub "123" :atr "456" :fallback "78"})))
    (is (= "78" (get-userid {:fallback "78"})))))

(defn- get-renamed-attribute [id-data]
  (let [rename-attrs (->> (getx env :oidc-userid-attributes)
                          (filter #(get id-data (keyword (:rename %)))))]
    (->> rename-attrs
         (some (fn [{:keys [attribute rename]}]
                 {:user-id (get id-data (keyword attribute))
                  :ext-id-attribute rename
                  :ext-id-value (get id-data (keyword rename))})))))

(deftest test-get-renamed-attribute
  (with-redefs [env {:oidc-userid-attributes [{:attribute "atr"}]}]
    (is (nil? (get-renamed-attribute {:sub "123"})))
    (is (nil? (get-renamed-attribute {:sub "123" :atr "456"}))))
  (with-redefs [env {:oidc-userid-attributes [{:attribute "atr"}
                                              {:attribute "sub" :rename "elixirId"}]}]
    (is (nil? (get-renamed-attribute {:sub "123"})))
    (is (= {:user-id "123" :ext-id-attribute "elixirId" :ext-id-value "456"}
           (get-renamed-attribute {:sub "123" :elixirId "456"})))
    (is (= {:user-id nil :ext-id-attribute "elixirId" :ext-id-value "456"}
           (get-renamed-attribute {:elixirId "456"})))))

(defn- get-mapped-userid [id-data]
  (when-let [attr (get-renamed-attribute id-data)]
    (user-mappings/get-user-mapping (:ext-id-attribute attr)
                                    (:ext-id-value attr))))

(defn create-user-mapping! [id-data]
  (let [attr (get-renamed-attribute id-data)]
    (when (some->> attr vals (every? some?))
      (user-mappings/create-user-mapping! attr))))

(defn get-user-attributes [id-data]
  (let [identity-base {:eppn (or (get-mapped-userid id-data) (get-userid id-data))
                       ;; need to maintain a fallback list of name attributes since identity
                       ;; providers differ in what they give us
                       :commonName (some id-data [:name :unique_name :family_name])
                       :mail (:email id-data)}
        extra-attributes (select-keys id-data (map (comp keyword :attribute) (:oidc-extra-attributes env)))]
    (merge identity-base extra-attributes)))

(defn get-researcher-status [user-info]
  (when-let [by (ga4gh/passport->researcher-status-by user-info)]
    {:researcher-status-by by}))

(defn oidc-callback [request]
  (let [response (-> (http/post (:token_endpoint oidc-configuration)
                                ;; NOTE Some IdPs don't support client id and secret in form params,
                                ;;      and require us to use HTTP basic auth
                                {:basic-auth [(str (getx env :oidc-client-id))
                                              (getx env :oidc-client-secret)]
                                 :form-params {:grant_type "authorization_code"
                                               :code (get-in request [:params :code])
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
                        json/parse-string))]
    (when (:log-authentication-details env)
      (log/info "logged in" id-data user-info))
    (-> (redirect "/redirect")
        (assoc :session (:session request))
        (assoc-in [:session :access-token] access-token)
        (assoc-in [:session :identity] (merge (get-user-attributes id-data)
                                              (get-researcher-status user-info))))))

(defn- oidc-revoke [token]
  (when token
    (when-let [endpoint (:revocation_endpoint oidc-configuration)]
      (let [response (http/post endpoint
                                {:basic-auth [(getx env :oidc-client-id)
                                              (getx env :oidc-client-secret)]
                                 :form-params {:token token}
                                 :throw-exceptions false})]
        (when-not (= 200 (:status response))
          (log/error "received HTTP status" (:status response) "from" endpoint))))))

; TODO Logout. Federated or not?
; TODO Silent login when we have a new session, but user has logged in to auth provider

(defroutes routes
  (GET "/oidc-login" _req (redirect (login-url)))
  (GET "/oidc-logout" req
    (let [session (get req :session)]
      (when (:log-authentication-details env)
        (log/info "logging out" (:identity session)))
      (oidc-revoke (:access-token session))
      (assoc (redirect "/") :session (dissoc session :identity :access-token))))
  (GET "/oidc-callback" req (oidc-callback req)))

