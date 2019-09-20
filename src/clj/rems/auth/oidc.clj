(ns rems.auth.oidc
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET defroutes]]
            [rems.config :refer [env]]
            [rems.json :as json]
            [rems.jwt :as jwt]
            [rems.util :refer [getx]]
            [ring.util.response :refer [content-type redirect response]])
  (:import [java.time Instant]))

(defn login-url []
  (str "https://"
       (getx env :oidc-domain)
       "/authorize?"
       "response_type=code"
       "&client_id=" (getx env :oidc-client-id)
       "&redirect_uri=" (getx env :public-url) "oidc-callback"
       "&scope=openid profile email"))
       ; "&state=STATE")) ; FIXME We could use the state for intelligent redirect. Also check if we need it for CSRF protection as Auth0 docs say.

(defn oidc-callback [request]
  (let [jwt (-> (http/post (str "https://"
                                (getx env :oidc-domain)
                                "/oauth/token")
                           {:form-params {:grant_type "authorization_code"
                                          :client_id (getx env :oidc-client-id)
                                          :client_secret (getx env :oidc-client-secret)
                                          :code (get-in request [:params :code])
                                          :redirect_uri (str (getx env :public-url) "oidc-callback")}})
                ; FIXME Complains about Invalid cookie header in logs
                ; TODO Unhandled responses for token endpoint:
                ;      403 {\"error\":\"invalid_grant\",\"error_description\":\"Invalid authorization code\"} when reusing codes
                (:body)
                (json/parse-string)
                (:id_token))
        issuer (str "https://" (getx env :oidc-domain) "/")
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
                                        :commonName (:name oidc-data)
                                        :mail (:email oidc-data)}))))

; TODO Logout. Federated or not?
; TODO Silent login when we have a new session, but user has logged in to auth provider

(defroutes routes
  (GET "/oidc-login" req (redirect (login-url)))
  (GET "/oidc-callback" req (oidc-callback req)))
