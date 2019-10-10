(ns rems.auth.oidc
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET defroutes]]
            [rems.config :refer [env oidc-configuration]]
            [rems.json :as json]
            [rems.jwt :as jwt]
            [rems.util :refer [getx]]
            [ring.util.response :refer [content-type redirect response]])
  (:import [java.time Instant]))

(defn login-url []
  (str (:authorization_endpoint oidc-configuration)
       "?response_type=code"
       "&client_id=" (getx env :oidc-client-id)
       "&redirect_uri=" (getx env :public-url) "oidc-callback"
       "&scope=openid profile email"))
       ; "&state=STATE")) ; FIXME We could use the state for intelligent redirect. Also check if we need it for CSRF protection as Auth0 docs say.

(defn logout-url []
  "/oidc-logout")

(defn oidc-callback [request]
  (let [jwt (-> (http/post (:token_endpoint oidc-configuration)
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
                (json/parse-string)
                (:id_token))
        issuer (:issuer oidc-configuration)
        audience (getx env :oidc-client-id)
        now (Instant/now)
        oidc-data (jwt/validate jwt issuer audience now)]
    ;; oidc-data has keys:
    ; sub – unique ID
    ; name - non-unique name
    ; locale – could be used to set preferred lang on first login
    ; email – non-unique (!) email
    (-> (redirect "/") ; TODO Could redirect with state param
        (assoc :session (:session request))
        (assoc-in [:session :identity] {:eppn (:sub oidc-data)
                                        ;; NOTE Some IdPs don't provide name, but family_name works
                                        :commonName (some oidc-data [:name :family_name])
                                        :mail (:email oidc-data)}))))

; TODO Logout. Federated or not?
; TODO Silent login when we have a new session, but user has logged in to auth provider

(defroutes routes
  (GET "/oidc-login" req (redirect (login-url)))
  (GET "/oidc-logout" req
    (let [session (get req :session)]
      (assoc (redirect "/#/redirect") :session (dissoc session :identity))))
  (GET "/oidc-callback" req (oidc-callback req)))
