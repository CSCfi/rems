(ns rems.api.testing
  "Shared code for API testing"
  (:require [cheshire.core :refer [parse-stream]]
            [clj-time.format :as time-format]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [mount.core :as mount]
            [muuntaja.core :as muuntaja]
            [rems.db.testing :refer [reset-db-fixture rollback-db-fixture test-data-fixture test-db-fixture caches-fixture search-index-fixture]]
            [rems.handler :refer :all]
            [rems.middleware]
            [rems.standalone]
            [ring.mock.request :refer :all]
            [rems.json :as json]))

(def ^{:doc "Run a full REMS HTTP server."} standalone-fixture
  (join-fixtures [test-db-fixture
                  reset-db-fixture
                  test-data-fixture
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

(def api-fixture-without-data
  (join-fixtures [test-db-fixture
                  rollback-db-fixture
                  handler-fixture
                  search-index-fixture
                  caches-fixture]))

(def api-fixture
  (join-fixtures [api-fixture-without-data
                  test-data-fixture]))

(defn authenticate [request api-key user-id]
  (-> request
      (assoc-in [:headers "x-rems-api-key"] api-key)
      (assoc-in [:headers "x-rems-user-id"] user-id)))

(defn assert-schema-errors
  "Try more advanced parsing for nicer schema errors."
  [response]
  (when (= 500 (:status response))
    (when-let [body (:body response)]
      (let [body (json/parse-string (slurp body))]
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

(defn assert-response-is-server-error? [response]
  (assert-schema-errors response)
  (assert (= 500 (:status response))))

(defn response-is-ok? [response]
  (assert-schema-errors response)
  (= 200 (:status response)))

(defn response-is-server-error? [response]
  (assert-schema-errors response)
  (= 500 (:status response)))

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
    :else val))

(defn read-body [{body :body :as response}]
  (let [content-type (get-in response [:headers "Content-Type"])]
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

(defn api-response [method api & [body api-key user-id]]
  (cond-> (request method api)
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

;;; Fake login without API key

(defn- strip-cookie-attributes [cookie]
  (re-find #"[^;]*" cookie))

(defn login-with-cookies [username]
  (let [login-headers (-> (request :get "/fake-login" {:username username})
                          handler
                          :headers)
        cookie (-> (get login-headers "Set-Cookie")
                   first
                   strip-cookie-attributes)]
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
