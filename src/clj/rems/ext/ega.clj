(ns rems.ext.ega
  "Utilities for interfacing with EGA Permission API.

  The function names try to match the remote API method and paths."
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [rems.db.user-secrets :as user-secrets]
            [rems.json :as json]
            [rems.util :refer [getx]]))

(def ^:private +common-opts+
  {:socket-timeout 2500
   :conn-timeout 2500
   :as :json})

(defn post-token
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
                                   "client_id" (getx config :client-id)
                                   "client_secret" (getx config :client-secret)
                                   "username" username
                                   "password" password
                                   "scope" "openid"}})))

(defn get-api-key-generate
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
  (http/get (str (getx config :permission-server-url) "/api_key/generate")
            (merge +common-opts+
                   {:oauth-token access-token
                    :query-params {"id" id
                                   "expiration_date" expiration-date ; TODO: pass as date?
                                   "reason" reason}})))

(defn delete-api-key-invalidate
  "Invalidates an API-Key of a user with a token.

  `:access-token`            - the access token
  `:id`                      - identity for the key e.g. user
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url"
  [{:keys [access-token id config]}]
  (assert access-token)
  (http/delete (str (getx config :permission-server-url) "/api_key/" id)
               (merge +common-opts+
                      {:oauth-token access-token})))

(defn get-api-key-list
  "List the API-Keys available.

  `:access-token` - the access token
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url

  NB: The actual API-Key is not returned by this call. It is only returned once in `api-key-generate`."
  [{:keys [access-token config]}]
  (http/get (str (getx config :permission-server-url) "/api_key")
            (merge +common-opts+
                   {:oauth-token access-token})))

(defn get-permissions
  "Gets the permissions of the specified user.

  `:api-key`                 - valid API-Key of the person acting
  `:account-id`              - account id (Elixir or EGA)
  `:format`                  - PLAIN or JWT, defaults to JWT.
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url"
  [{:keys [api-key account-id format config]}]
  (http/get (str (getx config :permission-server-url) "/permissions")
            (merge +common-opts+
                   {:headers {"authorization" (str "api-key " api-key)
                              "x-account-id" account-id}
                    :query-params {"format" (or format "JWT")}})))

(defn get-me-permissions
  "Gets the permissions of the current user.

  `:api-key`    - valid API-Key of the person acting
  `:format`     - PLAIN or JWT, defaults to JWT.
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url"
  [{:keys [api-key format config]}]
  (http/get (str (getx config :permission-server-url) "/me/permissions")
            (merge +common-opts+
                   {:headers {"authorization" (str "api-key " api-key)}
                    :query-params {"format" (or format "JWT")}})))

(defn get-dataset-users
  "Lists the users with permission to the dataset.

  `:api-key`                 - valid API-Key of the person acting
  `:dataset-id`              - id of the dataset
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url"
  [{:keys [api-key dataset-id config]}]
  (http/get (str (getx config :permission-server-url) "/datasets/" dataset-id "/users")
            (merge +common-opts+
                   {:headers {"authorization" (str "api-key " api-key)}})))

(defn post-create-or-update-permissions
  "Create or update the permissions of the user.

  `:api-key`                 - valid API-Key of the person acting
  `:account-id`              - account id (Elixir or EGA) of the user whose permissions are updated
  `:visas`                   - visas of permissions to create
  `:format`                  - PLAIN or JWT, defaults to JWT.
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url"
  [{:keys [api-key account-id visas format config] :as params}]
  (log/infof "%s: %s" #'post-create-or-update-permissions params)
  (http/post (str (getx config :permission-server-url) "/permissions")
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

(defn delete-permissions
  "Delete the permissions of the user for a given dataset.

  `:api-key`                 - valid API-Key of the person acting
  `:account-id`              - account id (Elixir or EGA) of the user whose permissions are deleted
  `:dataset-ids`             - ids of the datasets to delete
  `:config`                  - configuration of the EGA integration with following keys:
    `:permission-server-url` - EGA permission server url"
  [{:keys [api-key account-id dataset-ids config] :as params}]
  (log/infof "%s: %s" #'delete-permissions params)
  (http/delete (str (getx config :permission-server-url) "/permissions")
               (merge +common-opts+
                      {:headers {"authorization" (str "api-key " api-key)
                                 "x-account-id" account-id}
                       :query-params {:values (str/join "," dataset-ids)}})))
