(ns rems.user-simulator
  "Namespace for simulating actual user interactions with in REMS, allowing
   automated testing of user behavior. Simulations run in a headless browser
   using etaoin with chromedriver, and can be configured via the command line
   interface (CLI). Various user scenarios are implemented as functions in this
   namespace, enabling flexible testing of different workflows. Multiple
   simulations can run concurrently to test system performance and user load.
   
   User simulator can be started from CLI with e.g. Leiningen:
   - `lein run user-simulator` (uses default values), or
   - `lein run user-simulator http://localhost:3000/ alice,elsa,frank` when replacing default values"
  (:require [clj-http.client :as http]
            [clojure.pprint]
            [clojure.string :as str]
            [clojure.tools.logging.readable :as logr]
            [etaoin.api :as et]
            [mount.core :as mount]
            [muuntaja.core :as muuntaja]
            [rems.browser-test-util :as btu]
            [rems.common.util :refer [rand-nth*]]
            [rems.config]
            [rems.concurrency :as concurrency]
            [rems.json :as json]
            [rems.logging :refer [with-mdc]]))


;;; threadpool


(def ^:private thread-pool (atom nil))

(defn- submit-all [& fns]
  (let [pool (or @thread-pool
                 (reset! thread-pool (concurrency/cached-thread-pool {:thread-prefix "user-simulator"})))]
    (concurrency/submit! pool fns)))

(def task-counter (atom 0))
(def restart-queue (atom []))

(defn create-simulator-task [{:keys [actions url user-id] :as opts}]
  (logr/info #'create-simulator-task opts)
  (binding [btu/*test-context* (atom (assoc (btu/create-test-context)
                                            :url url))
            btu/screenshot (constantly nil)
            btu/screenshot-element (constantly nil)
            btu/check-axe (constantly nil)
            btu/postmortem-handler (constantly nil)]
    (bound-fn simulate-user []
      (try
        (logr/info "Simulator thread starting")
        (btu/init-driver! :chrome (btu/get-server-url))
        (btu/context-assoc! :user-id user-id)

        (while true
          (Thread/sleep (+ 200 (rand-int 300)))

          (when (btu/running?)
            (let [action-var (rand-nth actions)
                  action (var-get action-var)
                  task-id (swap! task-counter inc)]
              (btu/context-assoc! :task-id task-id)
              (with-mdc {:userid user-id
                         :request-id task-id}
                (et/delete-cookies (btu/get-driver))
                (btu/go (btu/get-server-url))
                (et/refresh (btu/get-driver))
                (btu/scroll-and-click [{:css ".language-switcher"} {:fn/text "EN"}]) ; make sure language is stable
                (action)))))

        (catch InterruptedException e
          (.interrupt (Thread/currentThread))
          (logr/info e "Simulator thread interrupted"))
        (catch Throwable t
          (logr/error t "Internal error" (with-out-str
                                           (clojure.pprint/pprint (merge {::context @(btu/test-ctx)}
                                                                         (ex-data t)))))
          (swap! restart-queue conj opts))
        (finally
          (logr/info "Simulator thread shutting down")
          (some-> (btu/get-driver)
                  et/stop-driver))))))


;;; api utils


(defn- parse-transit [x]
  (muuntaja/decode json/muuntaja "application/transit+json" x))

(defn- join-url [base path]
  (let [base (cond-> base
               (str/ends-with? base "/")
               (subs 0 (dec (count base))))
        path (cond-> path
               (str/starts-with? path "/")
               (subs 1))]
    (str base "/" path)))

(comment
  (= "http://localhost:3000/api/applications"
     (join-url "http://localhost:3000" "api/applications")
     (join-url "http://localhost:3000" "/api/applications")
     (join-url "http://localhost:3000/" "api/applications")
     (join-url "http://localhost:3000/" "/api/applications")))

(defn- api-get [path & [{:keys [api-key user-id]}]]
  (let [url (join-url (btu/get-server-url) path)
        response (http/get url {:accept :transit+json
                                :headers {"x-rems-api-key" (or api-key 42)
                                          "x-rems-user-id" (or user-id (btu/context-getx :user-id))}})]
    (parse-transit (:body response))))

(defn- query-my-applications []
  (let [user-id (btu/context-getx :user-id)]
    (->> (api-get "/api/applications")
         (filter #(= user-id (get-in % [:application/applicant :userid])))
         (mapv :application/id))))


;;; etaoin utils


(defn login-as [username]
  (btu/go (btu/get-server-url))
  (btu/scroll-and-click {:css ".login-btn"})
  (when (btu/visible? :show-special-users) ; sometimes the user is in the hidden part
    (btu/scroll-and-click :show-special-users))
  (btu/scroll-and-click [{:css ".users"} {:tag :a :fn/text username}])
  (btu/wait-visible :logout))

(defn logout []
  (btu/scroll-and-click :logout)
  (btu/wait-visible {:css ".login-component"}))

(defn click-navigation-menu [link-text]
  (btu/scroll-and-click [:big-navbar {:tag :a :fn/text link-text}]))

(defn go-to-application [application-id]
  (click-navigation-menu "Applications")
  (btu/wait-visible {:tag :h1 :fn/text "Applications – REMS"})
  (btu/wait-page-loaded)
  (btu/scroll-and-click [:my-applications
                         {:css (format "tr[data-row='%s'] > td.view a" application-id)}])
  (btu/wait-page-loaded))


;;; simulations


(defn user-views-applications
  "Simulation flow where
   - user logs in,
   - user visits random application pages (that user is applying for)
   - user logs out."
  []
  (logr/info "login")
  (login-as (btu/context-getx :user-id))
  (loop [n 0]
    (Thread/sleep 1000)
    (btu/context-assoc! :my-applications (query-my-applications))
    (btu/context-assoc! :application-id (rand-nth* (btu/context-getx :my-applications)))
    (cond
      (>= n 100)
      (do (logr/info "logout")
          (logout))

      (not (btu/context-getx :application-id))
      (do (logr/warnf "No applications found for user %s" (btu/context-getx :user-id))
          (recur (inc n)))

      :else
      (let [start (System/currentTimeMillis)]
        (logr/infof "> view application %s/%s, id %s" n 100 (btu/context-getx :application-id))
        (go-to-application (btu/context-getx :application-id))
        (logr/infof "< view application %s/%s, id %s (%sms)" n 100 (- (System/currentTimeMillis) start) (btu/context-getx :application-id))
        (recur (inc n))))))


;;; core logic


(def all-actions [#'user-views-applications])

(defn- queue-simulate-tasks! [opts]
  (assert (seq (:users opts)) "no users to simulate?")
  (assert (seq (:actions opts)) "no actions to simulate?")
  (assert (:url opts) "missing target REMS url")
  (logr/info 'queue-simulate-tasks :start opts)
  (submit-all (vec (for [user-id (:users opts)
                         :let [task-opts {:actions (:actions opts)
                                          :url (:url opts)
                                          :user-id user-id}]]
                     (create-simulator-task task-opts))))
  (add-watch restart-queue :task-daemon (fn [_key q _old-state new-state]
                                          (when (seq new-state)
                                            (submit-all (mapv create-simulator-task new-state))
                                            (reset! q [])))))

(defn start! [& [{:keys [actions url users]}]]
  (mount/start #'rems.config/env)
  (queue-simulate-tasks! {:actions (or (seq actions) all-actions)
                          :url (or url "http://localhost:3000/")
                          :users (or (seq users) ["alice" "elsa" "frank"])}))

(defn stop! []
  (remove-watch restart-queue :task-daemon)
  (some-> @thread-pool concurrency/shutdown-now!))

(comment
  (start! {:actions [#'user-views-applications]
           :url "http://localhost:3000/"
           :users ["alice" "elsa" "frank"]})
  (stop!))
