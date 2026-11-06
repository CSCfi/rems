(ns rems.event-notification
  (:require [buddy.core.nonce]
            [buddy.sign.jwe :as jwe]
            [clj-http.client :as http]
            [clj-time.coerce]
            [clj-time.core :as time]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [rems.api.schema :as schema]
            [rems.auth.dpop :as dpop]
            [rems.common.util :refer [getx]]
            [rems.config]
            [rems.context :as context]
            [rems.db.applications]
            [rems.db.outbox]
            [rems.json :as json]
            [rems.scheduler :as scheduler])
  (:import [java.time Instant]))

(def ^:private default-timeout 60)

;;; Forwarding the access token of the logged-in user (`:send-access-token?`)

;; Access tokens are stored in the outbox encrypted with a key that only
;; exists in the memory of this REMS process. After a restart the token
;; can't be sent anymore, just like the session it came from is gone.
(defonce ^:private access-token-key (delay (buddy.core.nonce/random-bytes 32)))

(def ^:private access-token-encryption {:alg :dir :enc :a256gcm})

(defn- encrypt-access-token [token]
  (jwe/encrypt token @access-token-key access-token-encryption))

(defn- decrypt-access-token [encrypted]
  (try
    (String. ^bytes (jwe/decrypt encrypted @access-token-key access-token-encryption) "UTF-8")
    (catch Exception _
      nil)))

(defn- current-access-token
  "The access token in the session of the ongoing request, if any."
  []
  (when (bound? #'context/*request*)
    (let [session (:session context/*request*)]
      (when-let [token (:access-token session)]
        {:token token
         :type (:access-token-type session)
         :expires-at (:access-token-expires-at session)
         :userid (get-in session [:identity :userid])}))))

(defn- access-token-for
  "Which access token, if any, should be sent to `target` with `event`."
  [target event access-token]
  (when (and access-token
             (:send-access-token? target)
             ;; e.g. events of bots reacting to the user's command don't get the token
             (= (:event/actor event) (:userid access-token)))
    access-token))

(deftest test-access-token-for
  (let [token {:token "abc" :userid "alice"}
        event {:event/actor "alice"}]
    (is (nil? (access-token-for {} event token)) "opt-in")
    (is (nil? (access-token-for {:send-access-token? true} event nil)))
    (is (= token (access-token-for {:send-access-token? true} event token)))
    (is (nil? (access-token-for {:send-access-token? true} {:event/actor "approver-bot"} token))
        "events of other actors don't get the token")))

(defn- stored-access-token
  "Returns the access token to send from an outbox entry, or `{:error ...}`
  if it can't be sent anymore."
  [{:keys [encrypted type expires-at]}]
  (if (and expires-at (<= expires-at (.getEpochSecond (Instant/now))))
    {:error "access token expired"}
    (if-let [token (decrypt-access-token encrypted)]
      {:token token :type type}
      {:error "access token unavailable"})))

(deftest test-stored-access-token
  (let [future-time (+ (.getEpochSecond (Instant/now)) 60)
        past-time (- (.getEpochSecond (Instant/now)) 60)]
    (is (= {:token "abc" :type "DPoP"}
           (stored-access-token {:encrypted (encrypt-access-token "abc") :type "DPoP" :expires-at future-time})))
    (is (= {:token "abc" :type nil}
           (stored-access-token {:encrypted (encrypt-access-token "abc")}))
        "no expiry known")
    (is (= {:error "access token expired"}
           (stored-access-token {:encrypted (encrypt-access-token "abc") :expires-at past-time})))
    (is (= {:error "access token unavailable"}
           (stored-access-token {:encrypted "not.a.valid.jwe.token"}))
        "e.g. encrypted before a restart")))

(defn- access-token-headers [url {:keys [token type]} nonce]
  (when token
    (merge {"Authorization" (dpop/authorization-header type token)}
           (when (dpop/dpop-token-type? type)
             {"DPoP" (dpop/proof {:htm :put :htu url :access-token token :nonce nonce})}))))

;;; Sending

(defn- notify!
  ([target body] (notify! target body nil))
  ([target body access-token]
   (log/info "Sending event notification for event" (select-keys body [:event/id :application/id :event/type :event/time])
             "to" (:url target))
   (try
     (let [timeout-ms (* 1000 (get target :timeout default-timeout))
           url (getx target :url)
           send! (fn [nonce]
                   (http/put url
                             {:body (json/generate-string body)
                              :throw-exceptions false
                              :content-type :json
                              :headers (merge (get target :headers)
                                              (access-token-headers url access-token nonce))
                              :socket-timeout timeout-ms
                              :conn-timeout timeout-ms}))
           response (if (dpop/dpop-token-type? (:type access-token))
                      (dpop/with-nonce-retry url send!)
                      (send! nil))
           status (:status response)]

       (if (= 200 status)
         (log/infof "Sent event notification for event %s -> %s" (select-keys body [:event/id :application/id :event/type :event/time]) status)
         (do
           (log/error "Event notification response status" status)
           (str "failed: " status))))

     (catch Exception e
       (log/error "Event notification failed" e)
       "failed: exception"))))

(defn process-outbox! []
  ;; TODO if we want to guarantee event ordering, we need fetch all
  ;; outbox entries here, and pick the one with the lowest outbox id
  ;; or event id, and do nothing if it isn't due yet.
  ;;
  ;; This can be done per target url or globally.
  (log/debug "Trying to send notifications")
  (let [due-notifications (rems.db.outbox/get-due-entries :event-notification)]
    (log/debug (str "Notifications due: " (count due-notifications)))
    (doseq [entry due-notifications
            :let [{:keys [target body access-token]} (:outbox/event-notification entry)
                  access-token (some-> access-token stored-access-token)]]
      (if-let [error (:error access-token)]
        (do
          (log/warn "not sending event notification id" (:outbox/id entry) "to" (:url target) "because" error)
          (rems.db.outbox/attempt-failed-fatally! (update entry :outbox/event-notification dissoc :access-token)
                                                  (str "failed: " error)))
        (if-let [error (notify! target body access-token)]
          (let [entry (rems.db.outbox/attempt-failed! entry error)]
            (when-not (:outbox/next-attempt entry)
              (log/warn "all attempts to send event notification id " (:outbox/id entry) "failed")))
          (rems.db.outbox/attempt-succeeded! (:outbox/id entry)))))))

(mount/defstate event-notification-poller
  :start (scheduler/start! "event-notification-poller" process-outbox! (.toStandardDuration (time/seconds 10)))
  :stop (scheduler/stop! event-notification-poller))

(defn- add-to-outbox! [target body access-token]
  (let [deadline (time/plus (time/now) (time/days 1)) ;; hardcoded for now
        token-expiry (some-> (:expires-at access-token) (* 1000) clj-time.coerce/from-long)]
    (rems.db.outbox/put! {:outbox/type :event-notification
                          ;; no point in retrying after the access token has expired
                          :outbox/deadline (if (and token-expiry (time/before? token-expiry deadline))
                                             token-expiry
                                             deadline)
                          :outbox/event-notification (cond-> {:target target
                                                              :body body}
                                                       access-token (assoc :access-token {:encrypted (encrypt-access-token (:token access-token))
                                                                                          :type (:type access-token)
                                                                                          :expires-at (:expires-at access-token)}))})))

(defn wants? [target event]
  (let [whitelist (:event-types target)]
    (or (empty? whitelist)
        (some? (some #{(:event/type event)} whitelist)))))

(deftest test-wants?
  (let [target {:url "whatever" :event-types [:application.event/submitted :application.event/approved]}]
    (is (false? (wants? target {:event/type :application.event/created})))
    (is (true? (wants? target {:event/type :application.event/submitted})))
    (is (true? (wants? target {:event/type :application.event/approved}))))
  (let [target {:url "whatever" :event-types []}]
    (is (true? (wants? target {:event/type :application.event/created})))
    (is (true? (wants? target {:event/type :application.event/submitted})))
    (is (true? (wants? target {:event/type :application.event/approved})))))

(defn queue-notifications! [events]
  (when-let [targets (seq (get rems.config/env :event-notification-targets))]
    (let [access-token (delay (current-access-token))]
      (doseq [event events
              :let [application-part (delay {:event/application (rems.db.applications/get-application (:application/id event))})]
              target targets
              :when (wants? target event)
              :let [body (merge event
                                (when (:send-application? target true)
                                  @application-part))]]
        (add-to-outbox! target body (access-token-for target event @access-token))))))
