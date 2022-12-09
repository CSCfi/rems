(ns rems.service.ega
  "Service for interfacing with EGA Permission API."
  (:require [clj-time.core :as time-core]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [medley.core :refer [find-first]]
            [rems.config :refer [env]]
            [rems.db.user-secrets :as user-secrets]
            [rems.db.user-settings :as user-settings]
            [rems.ext.ega :as ega]
            [rems.ga4gh :as ga4gh]
            [rems.util :refer [getx]]))

(defn get-ega-config
  "Returns the EGA push configuration.

  NB: This is currently limited to one EGA of configuration at a time."
  []
  (let [config (find-first (comp #{:ega} :type) (:entitlement-push env))]
    (assert (seq config) "EGA entitlement push must be configured!")

    config))

(defn- get-api-key-for [handler-userid]
  (get-in (user-secrets/get-user-secrets handler-userid) [:ega :api-key])) ; TODO: implement

(defn- get-dac-for [_resid _handler-userid]
  "EGAC00001000908")

(defn- entitlement->update
  "Converts an entitlement to a GA4GH visa for EGA.

  `entitlement` – entitlement to convert

  NB: the GA4GH signing keys will be required from the environment"
  [entitlement]
  {:format "JWT"
   :visas (-> [{:resid (:resid entitlement)
                :start (:start entitlement)
                :end (:end entitlement)
                :userid (:userid entitlement)
                :dac-id (get-dac-for (:resid entitlement) (:approvedby entitlement))}]
              ga4gh/entitlements->passport
              :ga4gh_passport_v1)})

(deftest test-entitlement->update
  (with-redefs [env (rems.config/load-keys {:public-url "https://rems.example/"
                                            :ga4gh-visa-private-key "test-data/example-private-key.jwk"
                                            :ga4gh-visa-public-key "test-data/example-public-key.jwk"})
                time-core/now (fn [] (time-core/date-time 2021 04 17))] ; iat
    (is (= {:format "JWT"
            :visas [[{:alg "RS256"
                      :kid "2011-04-29"
                      :jku "https://rems.example/api/jwk"
                      :typ "JWT"}
                     {:sub "elixir-user"
                      :iss "https://rems.example/"
                      :exp 1647475200
                      :ga4gh_visa_v1
                      {:value "EGAD00001006673"
                       :type "ControlledAccessGrants"
                       :source "EGAC00001000908"
                       :asserted 1615939200
                       :by "dac"}
                      :iat 1618617600}]]}
           (update-in (entitlement->update {:resid "EGAD00001006673"
                                            :userid "elixir-user"
                                            :approvedby "dac-user"
                                            :start (time-core/date-time 2021 03 17)
                                            :end (time-core/date-time 2022 03 17)})
                      [:visas 0]
                      ga4gh/visa->claims)))))

(defn entitlement-push [action entitlement config]
  (let [api-key (get-api-key-for (:approvedby entitlement))
        common-fields {:api-key api-key
                       :account-id (:userid entitlement)
                       :config config}]

    (when (str/blank? api-key)
      (log/warnf "Missing EGA api-key for %s" (:approvedby entitlement)))

    (log/infof "Pushing entitlements to %s %s: %s %s" (getx config :id) (getx config :permission-server-url) entitlement config)

    (let [response (case action
                     :add
                     (ega/post-create-or-update-permissions (merge common-fields
                                                                   (entitlement->update entitlement)))

                     :remove
                     (ega/delete-permissions (merge common-fields
                                                    {:dataset-ids [(:resid entitlement)]})))]
      (log/infof "Pushed entitlements to %s %s: %s %s -> %s" (getx config :id) (getx config :permission-server-url) entitlement config (:status response))
      response)))

(defn generate-api-key-with-access-token
  "Generates an API-Key with `access-token` and saves it to the user's secrets.

  `:userid`               - REMS user to generate the key for
  `:access-token`         - User's ELIXIR access token
  `:config`               - configuration of the EGA integration with following keys:
    `:connect-server-url` - EGA login server url
    `:permission-server-url` - EGA permission server url
    `:client-id`          - client id for REMS
    `:client-secret`      - client secret for REMS"
  [{:keys [userid access-token config]}]
  (assert userid "User missing!")

  (try
    (let [_ (log/info "Generate API-Key...")
          expiration-date (time-core/plus (time-core/now) (time-core/years 1))
          api-key (-> {:access-token access-token
                       :id userid
                       :expiration-date expiration-date
                       :reason "rems_ega_push"
                       :config config}
                      ega/get-api-key-generate
                      :body
                      :token)
          _ (assert api-key)

          _ (log/info "Save user secret...")
          secrets-result (user-secrets/update-user-secrets! userid {:ega {:api-key api-key}})
          _ (assert (:success secrets-result))

          _ (log/info "Save user settings...")
          settings-result (user-settings/update-user-settings! userid {:ega {:api-key-expiration-date expiration-date}})
          _ (assert (:success settings-result))]

      (log/info "Success!")
      {:success true
       :api-key-expiration-date expiration-date})

    (catch Throwable t
      (log/error t "Failure!")
      {:success false})))

(defn generate-api-key-with-account
  "Logs into EGA with `username` and `password` to fetch an access token, then generates an API-Key and saves it to the user's secrets.

  `:userid`               - REMS user to generate the key for
  `:username`             - EGA user to use
  `:password`             - EGA user password
  `:config`               - configuration of the EGA integration with following keys:
    `:connect-server-url` - EGA login server url
    `:permission-server-url` - EGA permission server url
    `:client-id`          - client id for REMS
    `:client-secret`      - client secret for REMS"
  [{:keys [userid username password config]}]
  (assert userid "User missing!")

  (let [_ (log/info "Get EGA token...")
        access-token (-> {:username username
                          :password password
                          :config config}
                         ega/post-token
                         :body
                         :access_token)
        _ (assert access-token)]
    (generate-api-key-with-access-token {:userid userid
                                         :access-token access-token
                                         :config config})))

(defn delete-api-key
  "Deletes the API-Key and removes it from the user's secrets.

  `:userid`               - REMS user to delete the key of
  `:access-token`         - User's ELIXIR access token
  `:config`               - configuration of the EGA integration with following keys:
    `:connect-server-url` - EGA login server url
    `:permission-server-url` - EGA permission server url
    `:client-id`          - client id for REMS
    `:client-secret`      - client secret for REMS"
  [{:keys [userid access-token config]}]
  (assert userid "User missing!")

  (try
    (let [_ (log/info "Deleting API-Key...")
          _result (-> {:access-token access-token
                       :id userid
                       :config config}
                      ega/delete-api-key-invalidate)

          _ (log/info "Remove user secret...")
          secrets-result (user-secrets/update-user-secrets! userid {:ega {}})
          _ (assert (:success secrets-result))

          _ (log/info "Remove user setting...")
          settings-result (user-settings/update-user-settings! userid {:ega {}})
          _ (assert (:success settings-result))]
      (log/info "Success!")
      {:success true})
    (catch Throwable t
      (do
        (log/error t "Failure!")
        {:success false}))))
