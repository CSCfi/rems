(ns rems.ega
  "Utilities for interfacing with EGA Permission API.

  The function names match the remote API method and paths.

  The most important function is the last but not least `entitlement-push`. The rest
  are so far kept private."
  (:require [clj-http.client :as http]
            [clj-time.core :as time-core]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [rems.config :refer [env]]
            [rems.db.user-secrets :as user-secrets]
            [rems.ga4gh :as ga4gh]
            [rems.json :as json]))

(def ^:private +common-opts+
  {:socket-timeout 2500
   :conn-timeout 2500
   :as :json})

(defn- post-token
  "Fetches an EGA Token representing an EGA user.

  `:username`             - username (in EGA) e.g. foo@bar.com
  `:password`             - password for the user
  `:config`               - configuration of the EGA integration with following keys:
    `:connect-server-url` - EGA login server url
    `:client-id`          - client id for REMS
    `:client-secret`      - client secret for REMS

  NB: It is valid for one hour."
  [{:keys [username password config]}]
  (http/post (str (:connect-server-url config) "/token")
             (merge +common-opts+
                    {:form-params {"grant_type" "password"
                                   "client_id" (:client-id config)
                                   "client_secret" (:client-secret config)
                                   "username" username
                                   "password" password
                                   "scope" "openid"}})))

(defn- get-api-key-generate
  "Generate an API-Key for a user with a token.

  The API-Key can be used longer (e.g. a year) to represent the
  user it was generated for.

  `:access-token`            - the access token
  `:id`                      - identity for the key e.g. user
  `:expiration-date`         - YYYY-MM-DD of the desired expiration date
  `:reason`                  - string description of the use of the key
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url"
  [{:keys [access-token id expiration-date reason config]}]
  (assert access-token)
  (http/get (str (:permission-server-url config) "/api_key/generate")
            (merge +common-opts+
                   {:oauth-token access-token
                    :query-params {"id" id
                                   "expiration_date" expiration-date ; TODO: pass as date?
                                   "reason" reason}})))

(defn- get-api-key-list
  "List the API-Keys available.

  `:access-token` - the access token
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url

  NB: The actual API-Key is not returned by this call. It is only returned once in `api-key-generate`."
  [{:keys [access-token config]}]
  (http/get (str (:permission-server-url config) "/api_key")
            (merge +common-opts+
                   {:oauth-token access-token})))

(defn- get-permissions
  "Gets the permissions of the specified user.

  `:api-key`                 - valid API-Key of the person acting
  `:account-id`              - account id (Elixir or EGA)
  `:format`                  - PLAIN or JWT, defaults to JWT.
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url"
  [{:keys [api-key account-id format config]}]
  (http/get (str (:permission-server-url config) "/permissions")
            (merge +common-opts+
                   {:headers {"authorization" (str "api-key " api-key)
                              "x-account-id" account-id}
                    :query-params {"format" (or format "JWT")}})))

(defn- get-me-permissions
  "Gets the permissions of the current user.

  `:api-key`    - valid API-Key of the person acting
  `:format`     - PLAIN or JWT, defaults to JWT.
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url"
  [{:keys [api-key format config]}]
  (http/get (str (:permission-server-url config) "/me/permissions")
            (merge +common-opts+
                   {:headers {"authorization" (str "api-key " api-key)}
                    :query-params {"format" (or format "JWT")}})))

(defn- get-dataset-users
  "Lists the users with permission to the dataset.

  `:api-key`                 - valid API-Key of the person acting
  `:dataset-id`              - id of the dataset
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url"
  [{:keys [api-key dataset-id config]}]
  (http/get (str (:permission-server-url config) "/datasets/" dataset-id "/users")
            (merge +common-opts+
                   {:headers {"authorization" (str "api-key " api-key)}})))

(defn- get-api-key-for [handler-userid]
  (get-in (user-secrets/get-user-secrets handler-userid) [:ega :api-key]))

(defn- get-dac-for [resid handler-userid]
  "EGAC00001000908") ; TODO: implement

(defn- post-create-or-update-permissions
  "Create or update the permissions of the user.

  `:api-key`                 - valid API-Key of the person acting
  `:account-id`              - account id (Elixir or EGA) of the user whose permissions are updated
  `:visas`                   - visas of permissions to create
  `:format`                  - PLAIN or JWT, defaults to JWT.
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url"
  [{:keys [api-key account-id visas format config] :as params}]
  (log/infof "%s: %s" #'post-create-or-update-permissions params)
  (http/post (str (:permission-server-url config) "/permissions")
             (merge +common-opts+
                    {:headers {"authorization" (str "api-key " api-key)
                               "x-account-id" account-id}
                     :content-type :json
                     :body (json/generate-string (if (= "JWT" format)
                                                   (map (fn [visa]
                                                          {:jwt visa
                                                           :format format})
                                                        visas)
                                                   visas))
                     :query-params {:format (or format "JWT")}})))

(defn- delete-permissions
  "Delete the permissions of the user for a given dataset.

  `:api-key`                 - valid API-Key of the person acting
  `:account-id`              - account id (Elixir or EGA) of the user whose permissions are deleted
  `:dataset-ids`             - ids of the datasets to delete
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url"
  [{:keys [api-key account-id dataset-ids config] :as params}]
  (log/infof "%s: %s" #'delete-permissions params)
  (http/delete (str (:permission-server-url config) "/permissions")
               (merge +common-opts+
                      {:headers {"authorization" (str "api-key " api-key)
                                 "x-account-id" account-id}
                       :query-params {:values (str/join "," dataset-ids)}})))

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

    (log/infof "Pushing entitlements to %s %s: %s %s" (:id config) (:permission-server-url config) entitlement config)

    (case action
      :add
      (post-create-or-update-permissions (merge common-fields
                                                (entitlement->update entitlement)))

      :remove
      (delete-permissions (merge common-fields
                                 {:dataset-ids [(:resid entitlement)]})))))

(defn generate-api-key
  "Generates an API-Key and saves it to the user's secrets.

  `:userid`               - REMS user to generate the key for
  `:access-token`         - User's ELIXIR access token
  `:config`               - configuration of the EGA integration with following keys:
    `:connect-server-url` - EGA login server url
    `:permission-server-url` - EGA permission server url
    `:client-id`          - client id for REMS
    `:client-secret`      - client secret for REMS"
  [{:keys [userid access-token config]}]
  (let [_ (log/info "Generate API-Key...")
        expiration-date (time-core/plus (time-core/now) (time-core/years 1))
        api-key (-> {:access-token access-token
                     :id userid
                     :expiration-date expiration-date
                     :reason "rems_ega_push"
                     :config config}
                    get-api-key-generate
                    :body
                    :token)
        _ (log/info "Save user secret...")
        result (user-secrets/update-user-secrets! userid {:ega {:api-key api-key}})]
    (if (:success result)
      (do
        (println "Success!")
        {:success (:success result)
         :api-key-expiration-date expiration-date})
      {:success false})))

(defn update-api-key
  "Logs into EGA fetching an access token, generates an API-Key and saves it to the user's secrets.

  `:userid`               - REMS user to generate the key for
  `:username`             - EGA user to use
  `:password`             - EGA user password
  `:config`               - configuration of the EGA integration with following keys:
    `:connect-server-url` - EGA login server url
    `:permission-server-url` - EGA permission server url
    `:client-id`          - client id for REMS
    `:client-secret`      - client secret for REMS"
  [{:keys [userid username password config]}]
  (let [_ (log/info "Get EGA token...")
        access-token (-> {:username username
                          :password password
                          :config config}
                         post-token
                         :body
                         :access_token)]
    (generate-api-key {:userid userid
                       :access-token access-token
                       :config config})))
