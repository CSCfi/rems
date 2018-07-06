(ns rems.api.public
  (:require [compojure.api.sweet :refer :all]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.locales :as locales]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(def GetTranslationsResponse
  s/Any)

(def GetThemeResponse
  s/Any)

(def ExtraPage
  {s/Keyword s/Any})

(def GetConfigResponse
  {:authentication s/Keyword
   (s/optional-key :alternative-login-url) s/Str
   (s/optional-key :extra-pages) [ExtraPage]
   (s/optional-key :default-language) s/Keyword})

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
      (ok context/*theme*))))

(def config-api
  (context "/config" []
    :tags ["config"]

    (GET "/" []
      :summary "Get configuration that is relevant to UI"
      :return GetConfigResponse
      (ok (select-keys env [:authentication :alternative-login-url :extra-pages :default-language])))))
