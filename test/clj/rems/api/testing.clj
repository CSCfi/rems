(ns rems.api.testing
  "Shared code for API testing"
  (:require [clj-time.format :as time-format]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [mount.core :as mount]
            [muuntaja.core :as muuntaja]
            [rems.db.testing :refer [reset-db-fixture rollback-db-fixture test-db-fixture reset-caches-fixture search-index-fixture]]
            [rems.handler :refer :all]
            [rems.locales]
            [rems.middleware]
            [rems.main]
            [ring.mock.request :refer :all]
            [rems.json :as json]))

(def ^{:doc "Run a full REMS HTTP server."} standalone-fixture
  (join-fixtures [test-db-fixture
                  reset-db-fixture
                  (fn [f]
                    (mount/start) ;; mount/stop is in test-db-fixture
                    (f))]))

(defn handler-fixture [f]
  (mount/start
   #'rems.locales/translations
   #'rems.middleware/session-store
   #'rems.handler/handler)
  ;; TODO: silence logging somehow?
  (f)
  (mount/stop
   #'rems.locales/translations
   #'rems.middleware/session-store
   #'rems.handler/handler))

(def api-fixture
  (join-fixtures [test-db-fixture
                  rollback-db-fixture
                  handler-fixture
                  search-index-fixture
                  reset-caches-fixture]))

(defn authenticate [request api-key user-id]
  (cond-> request
    api-key (assoc-in [:headers "x-rems-api-key"] api-key)
    user-id (assoc-in [:headers "x-rems-user-id"] user-id)))

(defn assert-schema-errors
  "Try more advanced parsing for nicer schema errors."
  [response]
  (when (= 500 (:status response))
    (when-let [body (:body response)]
      (let [body (if (string? body) body (json/parse-string (slurp body)))]
        (assert (and (not (:schema body))
                     (not (:errors body)))
                (let [errors (:errors body)]
                  (pr-str {:status (:status response)
                           :failed (vec (if (map? errors)
                                          (set (keys errors))
                                          (set (mapcat keys errors))))
                           :errors errors
                           :body body})))))))

(defn assert-response-is-ok [response]
  (assert response)
  (assert-schema-errors response)
  (assert (= 200 (:status response))
          (pr-str {:status (:status response)
                   :body (when-let [body (:body response)]
                           (if (string? body)
                             body
                             (slurp body)))}))
  response)

(defn assert-response-is-redirect [response]
  (assert response)
  (assert (= 302 (:status response))
          {:status (:status response)
           :body (when-let [body (:body response)]
                   (if (string? body)
                     body
                     (slurp body)))})
  response)

(defn assert-response-is-server-error? [response]
  (assert-schema-errors response)
  (assert (= 500 (:status response))))

(defn assert-response-is-unprocessable-entity [response]
  (assert-schema-errors response)
  (assert (= 422 (:status response))))

(defn response-is-ok? [response]
  (assert-schema-errors response)
  (= 200 (:status response)))

(defn response-is-bad-request? [response]
  (assert-schema-errors response)
  (= 400 (:status response)))

(defn response-is-unauthorized? [response]
  (assert-schema-errors response)
  (= 401 (:status response)))

(defn response-is-forbidden? [response]
  (assert-schema-errors response)
  (= 403 (:status response)))

(defn response-is-not-found? [response]
  (assert-schema-errors response)
  (= 404 (:status response)))

(defn response-is-unsupported-media-type? [response]
  (assert-schema-errors response)
  (= 415 (:status response)))

(defn response-is-payload-too-large? [response]
  (assert-schema-errors response)
  (= 413 (:status response)))

(defn response-is-server-error? [response]
  (assert-schema-errors response)
  (= 500 (:status response)))

(defn response-is-not-implemented? [response]
  (assert-schema-errors response)
  (= 501 (:status response)))

(defn logged-in? [response]
  (str/includes? (get-in response [:headers "x-rems-roles"])
                 "logged-in"))

(defn coll-is-empty? [data]
  (and (coll? data)
       (empty? data)))

(defn coll-is-not-empty? [data]
  (not (coll-is-empty? data)))

(defn transit-body [request body-value]
  (-> request
      (content-type "application/transit+json")
      (body (let [t (slurp (muuntaja/encode json/muuntaja "application/transit+json" body-value))]
              (prn :TRANSIT t)
              t))
      (header "Accept" "application/transit+json")))

(defn ensure-string [val]
  (cond
    (instance? java.io.InputStream val) (slurp val)
    (instance? java.io.File val) (slurp val)
    :else val))

(defn get-content-type [response]
  (get-in response [:headers "Content-Type"]))

(defn read-body [{body :body :as response}]
  (let [content-type (get-content-type response)]
    (cond
      (.startsWith content-type "application/json") ;; might be "application/json; charset=utf-8"
      (json/parse-string (ensure-string body))

      (.startsWith content-type "application/transit+json")
      (muuntaja/decode json/muuntaja "application/transit+json" (ensure-string body))

      :else
      (ensure-string body))))

(defn read-ok-body [response]
  (assert-response-is-ok response)
  (read-body response))

(defn read-body-and-status [response]
  {:body (read-body response)
   :status (:status response)})

(defn api-response [method api & [body api-key user-id query-params]]
  (cond-> (request method api query-params)
    api-key (assoc-in [:headers "x-rems-api-key"] api-key)
    user-id (assoc-in [:headers "x-rems-user-id"] user-id)
    body (json-body body)
    true handler))

(defn api-call [method api body api-key user-id]
  (-> (api-response method api body api-key user-id)
      read-ok-body))

(defn assert-success [body]
  (assert (:success body) (pr-str body))
  body)

(defn get-redirect-location [response]
  (get-in response [:headers "Location"]))

;;; Fake login without API key

(defn- strip-cookie-attributes [cookie]
  (when cookie
    (re-find #"[^;]*" cookie)))

(defn- get-cookie [response]
  (-> response
      :headers
      (get "Set-Cookie")
      first
      strip-cookie-attributes))

(defn login-with-cookies [username]
  (let [cookie (-> (request :get "/fake-login" {:username username})
                   handler
                   assert-response-is-redirect
                   get-cookie)]
    (assert cookie)
    cookie))

(defn- parse-csrf-token [response]
  (let [token-regex #"var csrfToken = '([^\']*)'"
        [_ token] (re-find token-regex (:body response))]
    token))

(defn get-csrf-token [cookie]
  (let [csrf (-> (request :get "/")
                 (header "Cookie" cookie)
                 handler
                 parse-csrf-token)]
    (assert csrf)
    csrf))

(defn add-login-cookies [request user-id]
  (let [cookie (login-with-cookies user-id)
        csrf (get-csrf-token cookie)]
    (-> request
        (header "Cookie" cookie)
        (header "x-csrf-token" csrf))))

(defn valid-date? [x]
  (and (string? x)
       (time-format/parse (time-format/formatters :date-time) x)))

(defn parse-date [x]
  (when (string? x)
    (time-format/parse (time-format/formatters :date-time) x)))
