(ns rems.api.public
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.service.public :as public]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetTranslationsResponse
  s/Any)

(s/defschema GetThemeResponse
  s/Any)

(s/defschema ExtraPage
  {:id s/Str
   (s/optional-key :filename) s/Str
   (s/optional-key :url) s/Str
   (s/optional-key :roles) #{s/Keyword}
   (s/optional-key :show-menu) s/Bool
   (s/optional-key :show-footer) s/Bool
   (s/optional-key :heading) s/Bool
   (s/optional-key :translations) {s/Keyword {(s/optional-key :title) s/Str
                                              (s/optional-key :filename) s/Str
                                              (s/optional-key :url) s/Str}}})

(s/defschema GetConfigResponse
  {:authentication s/Keyword
   :alternative-login-url (s/maybe s/Str)
   :application-id-column (s/enum :id :external-id :generated-and-assigned-external-id)
   :catalogue-is-public s/Bool
   :extra-pages [ExtraPage]
   :enable-assign-external-id-ui s/Bool
   :attachment-max-size (s/maybe s/Int)
   :entitlement-default-length-days (s/maybe s/Int)
   :languages [s/Keyword]
   :default-language s/Keyword
   :oidc-extra-attributes [{:attribute s/Str
                            s/Keyword s/Any}]
   :dev s/Bool
   (s/optional-key :enable-ega) s/Bool
   (s/optional-key :enable-doi) s/Bool
   (s/optional-key :enable-duo) s/Bool
   (s/optional-key :enable-catalogue-table) s/Bool
   (s/optional-key :enable-catalogue-tree) s/Bool
   (s/optional-key :catalogue-tree-show-matching-parents) s/Bool
   (s/optional-key :enable-cart) s/Bool
   (s/optional-key :application-list-hidden-columns) [s/Keyword]
   (s/optional-key :enable-autosave) s/Bool
   (s/optional-key :show-resources-section) s/Bool
   (s/optional-key :show-attachment-zip-action) s/Bool
   (s/optional-key :show-pdf-action) s/Bool})

(def translations-api
  (context "/translations" []
    :tags ["translations"]

    (GET "/" []
      :summary "Get translations"
      :return GetTranslationsResponse
      (ok (public/get-translations)))))

(def theme-api
  (context "/theme" []
    :tags ["theme"]

    (GET "/" []
      :summary "Get current layout theme"
      :return GetThemeResponse
      (ok (public/get-theme)))))

(def config-api
  (context "/config" []
    :tags ["config"]

    (GET "/" []
      :summary "Get configuration that is relevant to UI"
      :return GetConfigResponse
      (ok (public/get-config)))

    (GET "/full" []
      :summary "Get (almost) full configuration"
      :roles #{:owner}
      :return s/Any
      (ok (public/get-config-full)))))

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
