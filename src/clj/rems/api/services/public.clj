(ns rems.api.services.public
  (:require [rems.config :refer [env]]
            [rems.locales :as locales]))

(defn get-translations []
  locales/translations)

(defn get-theme []
  (:theme env))

(defn get-config []
  (select-keys env [:alternative-login-url
                    :application-id-column
                    :authentication
                    :catalogue-is-public
                    :default-language
                    :dev
                    :entitlement-default-length-days
                    :extra-pages
                    :languages
                    :oidc-extra-attributes
                    :enable-assign-external-id-ui
                    :enable-ega
                    :enable-doi
                    :enable-duo
                    :attachment-max-size
                    :enable-catalogue-table
                    :enable-catalogue-tree
                    :catalogue-tree-show-matching-parents
                    :enable-cart
                    :application-list-hidden-columns
                    :enable-autosave]))

(defn get-config-full []
  (assoc env
         :authentication "HIDDEN"
         :database-url "HIDDEN"
         :test-database-url "HIDDEN"
         :oidc-client-secret "HIDDEN"
         :ga4gh-visa-private-key "HIDDEN"
         :ga4gh-visa-public-key "HIDDEN"))
