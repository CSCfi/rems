(ns rems.auth.oidc
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET defroutes]]
            [rems.config :refer [env oidc-configuration]]
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
       "&scope=openid profile email"
       #_"&state=STATE")) ; FIXME We could use the state for intelligent redirect. Also check if we need it for CSRF protection as Auth0 docs say.

(defn logout-url []
  "/oidc-logout")

(defn oidc-callback [request]
  (let [response (-> (http/post (:token_endpoint oidc-configuration)
                                        ; NOTE Some IdPs don't support client id and secret in form params,
                                        ;      and require us to use HTTP basic auth
                                {:basic-auth [(getx env :oidc-client-id)
                                              (getx env :oidc-client-secret)]
                                 :form-params {:grant_type "authorization_code"
                                               :code (get-in request [:params :code])
                                               :redirect_uri (str (getx env :public-url) "oidc-callback")}})
                                        ; FIXME Complains about Invalid cookie header in logs
                                        ; TODO Unhandled responses for token endpoint:
                                        ;      403 {\"error\":\"invalid_grant\",\"error_description\":\"Invalid authorization code\"} when reusing codes
                     (:body)
                     (json/parse-string))
        access-token (:access_token response)
        id-token (:id_token response)
        issuer (:issuer oidc-configuration)
        audience (getx env :oidc-client-id)
        now (Instant/now)
        id-data (jwt/validate id-token issuer audience now)]
    ;; id-data has keys:
    ; sub – unique ID
    ; name - non-unique name
    ; locale – could be used to set preferred lang on first login
    ; email – non-unique (!) email
    (when (:log-authentication-details env)
      (log/info "logged in" id-data))
    (-> (redirect "/") ; TODO Could redirect with state param
        (assoc :session (:session request))
        (assoc-in [:session :access-token] access-token)
        (assoc-in [:session :identity] {:eppn (:sub id-data)
                                        ;; need to maintain a fallback list of name attributes since identity
                                        ;; providers differ in what they give us
                                        :commonName (some id-data [:name :unique_name :family_name])
                                        :mail (:email id-data)}))))

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
