(ns rems.ega
  "Utilities for interfacing with EGA Permission API.

  The function names match the remote API paths."
  (:require [clj-http.client :as http]
            [clj-time.core :as time-core]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [rems.config :refer [env]]
            [rems.db.user-secrets :as user-secrets]
            [rems.ga4gh :as ga4gh]
            [rems.json :as json]))

(defn token
  "Fetches an EGA Token representing an EGA user.

  `:username`      - username (in EGA) e.g. foo@bar.com
  `:password`      - password for the user
  `:client-id`     - client id for REMS
  `:client-secret` - client secret for REMS
  `:config`        - configuration of the EGA integration

  NB: It is valid for one hour."
  [{:keys [username password client-id client-secret config]}]
  (-> (http/post (str (:connect-server-url config) "/token")
                 {:form-params {"grant_type" "password"
                                "client_id" client-id
                                "client_secret" client-secret
                                "username" username
                                "password" password
                                "scope" "openid"}
                  :socket-timeout 2500
                  :conn-timeout 2500})
      :body
      json/parse-string))

(defn api-key-generate
  "Generate an API-Key for a user with a token.

  The API-Key can be used longer (e.g. a year) to represent the
  user it was generated for.

  `:access-token`    - the access token
  `:id`              - identity for the key e.g. user
  `:expiration-date` - YYYY-MM-DD of the desired expiration date
  `:reason`          - string description of the use of the key
  `:config`          - configuration of the EGA integration"
  [{:keys [access-token id expiration-date reason config]}]
  (-> (http/get (str (:permission-server-url config) "/api_key/generate")
                {:oauth-token access-token
                 :query-params {"id" id
                                "expiration_date" expiration-date ; TODO: pass as date?
                                "reason" reason}
                 :socket-timeout 2500
                 :conn-timeout 2500})
      :body
      json/parse-string))

(defn api-key-list
  "List the API-Keys available.

  `:access-token` - the access token
  `:config`       - configuration of the EGA integration

  NB: The token itself cannot be returned anymore."
  [{:keys [access-token config]}]
  (-> (http/get (str (:permission-server-url config) "/api_key")
                {:oauth-token access-token
                 :socket-timeout 2500
                 :conn-timeout 2500})
      :body
      json/parse-string))

(defn read-permissions
  "Reads the permissions of the specified user.

  `:api-key`    - valid API-Key of the person asking
  `:account-id` - account id (Elixir or EGA)
  `:format`     - PLAIN or JWT, defaults to JWT.
  `:config`     - configuration of the EGA integration"
  [{:keys [api-key account-id format config]}]
  (-> (http/get (str (:permission-server-url config) "/permissions")
                {:headers {"authorization" (str "api-key " api-key)
                           "x-account-id" account-id}
                 :query-params {"format" (or format "JWT")}
                 :socket-timeout 2500
                 :conn-timeout 2500})
      :body
      json/parse-string))

(defn read-me-permissions
  "Reads the permissions of the current user.

  `:api-key`    - valid API-Key of the person asking
  `:format`     - PLAIN or JWT, defaults to JWT.
  `:config`     - configuration of the EGA integration"
  [{:keys [api-key format config]}]
  (-> (http/get (str (:permission-server-url config) "/me/permissions")
                {:headers {"authorization" (str "api-key " api-key)}
                 :query-params {"format" (or format "JWT")}
                 :socket-timeout 2500
                 :conn-timeout 2500})
      :body
      json/parse-string))

(defn datasets-list-users
  "Lists the users with permission to the dataset.

  `:api-key`    - valid API-Key of the person asking
  `:dataset-id` - id of the dataset
  `:config`     - configuration of the EGA integration"
  [{:keys [api-key dataset-id config]}]
  (-> (http/get (str (:permission-server-url config) "/datasets/" dataset-id "/users")
                {:headers {"authorization" (str "api-key " api-key)}
                 :socket-timeout 2500
                 :conn-timeout 2500})
      :body
      json/parse-string))

(defn get-api-key-for [handler-userid]
  (:ega-api-key (user-secrets/get-user-secrets handler-userid)))

(defn get-dac-for [resid handler-userid]
  "EGAC00001000908") ; TODO: implement

(defn create-or-update-permissions
  "Create or update the permissions of the user.

  `:api-key`    - valid API-Key of the person asking
  `:account-id` - account id (Elixir or EGA)
  `:visas`      - visas of permissions to create
  `:format`     - PLAIN or JWT, defaults to JWT.
  `:config`     - configuration of the EGA integration"
  [{:keys [api-key account-id visas format config]}]
  (-> (http/post (str (:permission-server-url config) "/permissions")
                 {:headers {"authorization" (str "api-key " api-key)
                            "x-account-id" account-id}
                  :body (json/generate-string (if (= "JWT" format)
                                                (map (fn [visa]
                                                       {:jwt visa
                                                        :format format})
                                                     visas)
                                                visas))
                  :content-type :json
                  :socket-timeout 2500
                  :conn-timeout 2500
                  :query-params {:format (or format "JWT")}})
      :body
      json/parse-string))

(defn delete-permissions
  "Delete the permissions of the user for a given dataset.

  `:api-key`     - valid API-Key of the person asking
  `:account-id`  - account id (Elixir or EGA)
  `:dataset-ids` - ids of the datasets to delete
  `:config`      - configuration of the EGA integration"
  [{:keys [api-key account-id dataset-ids config]}]
  (-> (http/delete (str (:permission-server-url config) "/permissions")
                   {:headers {"authorization" (str "api-key " api-key)
                              "x-account-id" account-id}
                    :socket-timeout 2500
                    :conn-timeout 2500
                    :query-params {:values (str/join "," dataset-ids)}})
      :body
      json/parse-string))

(defn- entitlement->update
  "Converts an entitlement to a GA4GH visa for EGA.

  `entitlement` – entitlement to convert

  NB: the GA4GH signing keys will be required from the environment"
  [entitlement]
  {:format "JWT"
   :visas [(-> [{:resid (:resid entitlement)
                 :start (:start entitlement)
                 :end (:end entitlement)
                 :userid (:userid entitlement)
                 :dac-id (get-dac-for (:resid entitlement) (:handler entitlement))}]
               ga4gh/entitlements->passport
               :ga4gh_passport_v1)]})

(deftest test-entitlement->update
  (with-redefs [env (rems.config/load-keys {:public-url "https://rems.example/"
                                            :ga4gh-visa-private-key "test-data/example-private-key.jwk"
                                            :ga4gh-visa-public-key "test-data/example-public-key.jwk"})
                time-core/now (fn [] (time-core/date-time 2021 04 17))] ; iat
    (is (= {:format "JWT"
            :visas [[[{:alg "RS256"
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
                       :iat 1618617600}]]]}
           (update-in (entitlement->update {:resid "EGAD00001006673"
                                            :userid "elixir-user"
                                            :start (time-core/date-time 2021 03 17)
                                            :end (time-core/date-time 2022 03 17)})
                      [:visas 0 0]
                      ga4gh/visa->claims)))))

(defn entitlement-push [action entitlement config]
  (let [common-fields {:api-key (get-api-key-for (:handler entitlement))
                       :account-id (:userid entitlement)
                       :config config}]

    (log/infof "Pushing entitlements to %s: %s" (:permission-server-url config) entitlement)

    (case action
      :add
      (create-or-update-permissions (merge common-fields
                                           (entitlement->update entitlement)))

      :remove
      (delete-permissions (merge common-fields
                                 {:dataset-ids [(:resid entitlement)]})))))
