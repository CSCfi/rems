(ns rems.api.public
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.auth.util :refer [throw-unauthorized]]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.locales :as locales]
            [rems.util :refer [get-user-id]]
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
   (s/optional-key :extra-pages) [ExtraPage]})

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
                (ok (select-keys env [:authentication :extra-pages])))))
