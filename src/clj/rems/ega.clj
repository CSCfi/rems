(ns rems.ega
  "Utilities for interfacing with EGA Permission API.

  The function names match the remote API paths."
  (:require [clj-http.client :as http]
            [clj-time.coerce :as time-coerce]
            [clj-time.core :as time-core]
            [clojure.string :as str]
            [rems.config :refer [env]]
            [rems.json :as json]))


(defn token
  "Fetches an EGA Token representing an EGA user.

  `:username`      - username (in EGA) e.g. foo@bar.com
  `:password`      - password for the user
  `:client-id`     - client id for REMS
  `:client-secret` - client secret for REMS

  NB: It is valid for one hour."
  [{:keys [username password client-id client-secret]}]
  (-> (http/post "https://ega.ebi.ac.uk:8053/ega-openid-connect-server/token"
                 {:form-params {"grant_type" "password"
                                "client_id" client-id
                                "client_secret" client-secret
                                "username" username
                                "password" password
                                "scope" "openid"}})
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
  "
  [{:keys [access-token id expiration-date reason]}]
  (-> (http/get "https://ega.ebi.ac.uk:8053/ega-permissions/api_key/generate"
                {:oauth-token access-token
                 :query-params {"id" id
                                "expiration_date" expiration-date ; TODO: pass as date?
                                "reason" reason}})
      :body
      json/parse-string))

(defn api-key-list
  "List the API-Keys available.

  `:access-token` - the access token

  NB: The token itself cannot be returned anymore."
  [{:keys [access-token]}]
  (-> (http/get "https://ega.ebi.ac.uk:8053/ega-permissions/api_key"
                {:oauth-token access-token})
      :body
      json/parse-string))

(defn read-permissions
  "Reads the permissions of the specified user.

  `:api-key`    - valid API-Key of the person asking
  `:account-id` - account id (Elixir or EGA)
  `:format`     - PLAIN or JWT, defaults to JWT."
  [{:keys [api-key account-id format]}]
  (-> (http/get "https://ega.ebi.ac.uk:8053/ega-permissions/permissions"
                {:headers {"authorization" (str "api-key " api-key)
                           "x-account-id" account-id}
                 :query-params {"format" (or format "JWT")}})
      :body
      json/parse-string))

(defn read-me-permissions
  "Reads the permissions of the current user.

  `:api-key`    - valid API-Key of the person asking
  `:format`     - PLAIN or JWT, defaults to JWT."
  [{:keys [api-key format]}]
  (-> (http/get "https://ega.ebi.ac.uk:8053/ega-permissions/me/permissions"
                {:headers {"authorization" (str "api-key " api-key)}
                 :query-params {"format" (or format "JWT")}})
      :body
      json/parse-string))

(defn datasets-list-users
  "Lists the users with permission to the dataset.

  `:api-key`    - valid API-Key of the person asking
  `:dataset-id` - id of the dataset"
  [{:keys [api-key dataset-id]}]
  (-> (http/get (str "https://ega.ebi.ac.uk:8053/ega-permissions/datasets/" dataset-id "/users")
                {:headers {"authorization" (str "api-key " api-key)}})
      :body
      json/parse-string))

(defn create-or-update-permissions
  "Create or update the permissions of the user.

  `:api-key`    - valid API-Key of the person asking
  `:account-id` - account id (Elixir or EGA)
  `:visas`      - permission visas
  `:format`     - PLAIN or JWT, defaults to JWT."
  [{:keys [api-key account-id visas format]}]
  (-> (http/post (str "https://ega.ebi.ac.uk:8053/ega-permissions/permissions")
                 {:headers {"authorization" (str "api-key " api-key)
                            "x-account-id" account-id}
                  :body (json/generate-string (if (= "JWT" format)
                                                (map (fn [visa]
                                                       {:jwt visa
                                                        :format format})
                                                     visas)
                                                visas))
                  :content-type :json
                  :query-params {:format (or format "JWT")}})
      :body
      json/parse-string))

(defn delete-permissions
  "Delete the permissions of the user for a given dataset.

  `:api-key`     - valid API-Key of the person asking
  `:account-id`  - account id (Elixir or EGA)
  `:dataset-ids` - ids of the datasets to delete"
  [{:keys [api-key account-id dataset-ids]}]
  (-> (http/delete (str "https://ega.ebi.ac.uk:8053/ega-permissions/permissions")
                   {:headers {"authorization" (str "api-key " api-key)
                              "x-account-id" account-id}
                    :query-params {:values (str/join "," dataset-ids)}})
      :body
      json/parse-string))
