(ns rems.api.public
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.config :refer [env]]
            [rems.locales :as locales]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetTranslationsResponse
  s/Any)

(s/defschema GetThemeResponse
  s/Any)

(s/defschema ExtraPage
  {:id s/Str
   (s/optional-key :url) s/Str
   :translations {s/Keyword {:title s/Str
                             (s/optional-key :filename) s/Str}}})

(s/defschema GetConfigResponse
  {:authentication s/Keyword
   :alternative-login-url (s/maybe s/Str)
   :application-id-column (s/enum :id :external-id)
   :catalogue-is-public s/Bool
   :extra-pages [ExtraPage]
   :languages [s/Keyword]
   :default-language s/Keyword
   :dev s/Bool})

(def translations-api
  (context "/translations" []
    :tags ["translations"]

    (GET "/" []
      :summary "Get translations"
      :return GetTranslationsResponse
      (ok locales/translations))))

(def theme-api
  (context "/theme" []
    :tags ["theme"]

    (GET "/" []
      :summary "Get current layout theme"
      :return GetThemeResponse
      (ok (:theme env)))))

(def config-api
  (context "/config" []
    :tags ["config"]

    (GET "/" []
      :summary "Get configuration that is relevant to UI"
      :return GetConfigResponse
      (ok (select-keys env [:alternative-login-url
                            :application-id-column
                            :authentication
                            :catalogue-is-public
                            :default-language
                            :dev
                            :extra-pages
                            :languages])))

    (GET "/full" []
      :summary "Get (almost) full configuration"
      :roles #{:owner}
      :return s/Any
      (ok (assoc env
                 :authentication "HIDDEN"
                 :database-url "HIDDEN"
                 :test-database-url "HIDDEN"
                 :oidc-client-secret "HIDDEN")))))

(def keepalive-api
  (context "/keepalive" []
    :tags ["keepalive"]
    (GET "/" []
      :summary "Restarts session timeout."
      ;; We use ring-ttl-session, which uses an expiring map to track sessions.
      ;; We install the wrap-session middleware via wrap-defaults in rems.middleware.
      ;; The session middleware looks up the user's session, and doing
      ;; so refreshes the key in the expiring map.
      (ok))))
