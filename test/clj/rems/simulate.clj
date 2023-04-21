(ns rems.simulate
  "Run load simulator from REMS. Simulator is configured with CLI options,
   and can be run standalone or using REPL. Functions are provided for simulating
   concurrent users via headless webdriver using etaoin. When running load simulator
   against local REMS, you may wish to set config option :accessibility-report false."
  (:require [clj-time.core :as time]
            [clojure.pprint :refer [pprint]]
            [clojure.set]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [rems.browser-test-util :as btu]
            [rems.db.applications]
            [rems.logging :refer [with-mdc]]
            [rems.scheduler :as scheduler]
            [rems.service.test-data :as test-data]
            [rems.simulate-util :as simu]
            [rems.test-browser :as b]
            [rems.util :refer [rand-nth*]])
  (:import [java.util.concurrent Executors ExecutorService TimeUnit]))

(defonce ^:private task-counter (atom 0))
(defonce ^:private current-tasks (atom {}))

(defn submit-new-application []
  (simu/with-test-browser
    (b/login-as (btu/context-get :user-id))
    (btu/context-assoc! :cat-item (simu/get-random-catalogue-item))
    (b/go-to-catalogue)
    (b/add-to-cart (btu/context-get :cat-item))
    (b/click-cart-apply)
    (btu/context-assoc! :application-id (b/get-application-id))
    (doseq [field (simu/get-application-fields (btu/context-get :application-id))
            :when (:field/visible field) ; ignore conditional fields for now
            :when (not (:field/optional field))] ; ignore optional fields
      (simu/fill field))
    (Thread/sleep 2000) ; wait for ui to catch up
    (when (btu/exists? :accept-licenses-button)
      (b/accept-licenses))
    (b/send-application)
    (Thread/sleep 2000)
    (b/logout)))

;; XXX: add more actions, e.g. view pdf, return/approve/reject
(defn handle-application []
  (simu/with-test-browser
    (b/login-as (btu/context-get :user-id))
    (btu/context-assoc! :application-id (-> (btu/context-get :user-id)
                                            (simu/get-random-todo-application)
                                            :application/id))
    (b/go-to-application (btu/context-get :application-id))
    (btu/scroll-and-click :remark-action-button)
    (let [selector {:id (b/get-field-id "Add remark")}]
      (simu/fill-human selector (test-data/random-long-string 5)))
    (btu/scroll-and-click :remark)
    (btu/wait-visible :status-success)
    (Thread/sleep 2000)
    (b/logout)))

(defn view-application []
  (simu/with-test-browser
    (b/login-as (btu/context-get :user-id))
    (b/go-to-applications)
    (-> (btu/context-get :user-id)
        (simu/get-random-application)
        :application/id
        (b/go-to-application))
    (Thread/sleep 2000)
    (b/logout)))

(comment
  ; convenience for development
  (btu/init-driver! :chrome "http://localhost:3000/" :development)
  (b/logout)

  (btu/context-assoc! :user-id "alice")
  (view-application)

  (btu/context-assoc! :user-id "handler")
  (handle-application)

  (btu/context-assoc! :user-id "alice")
  (with-redefs [simu/get-random-catalogue-item (constantly "Default workflow 2")]
    (submit-new-application)))

(defn get-task-action [user-id]
  (let [role (-> user-id
                 (rems.db.applications/get-all-application-roles)
                 (rand-nth*))
        actions (case role
                  :handler [handle-application]
                  [submit-new-application view-application])]
    (rand-nth* actions)))

(defn get-task-user []
  (->> @current-tasks
       (vals)
       (into #{} (map :user-id))
       (clojure.set/difference (simu/get-available-users))
       (rand-nth*)))

(defn create-task! [url]
  (let [task-id (swap! task-counter inc)
        task-context (atom (assoc (btu/create-base-context)
                                  :url url
                                  :seed "simulator"
                                  :task-id task-id))]
    (swap! current-tasks assoc task-id {:completed (list)})
    (fn simulate []
      (log/info "Simulator thread starting")
      (binding [btu/*test-context* task-context]
        (try
          (btu/init-driver! :chrome (btu/get-server-url))
          (while true
            (let [start (System/nanoTime)
                  user-id (get-task-user)]
              (if-not user-id
                (Thread/sleep 2000) ; avoid hogging CPU when nothing to do
                (with-mdc {:userid user-id}
                  (swap! current-tasks assoc-in [task-id :user-id] user-id)
                  (log/info "task >")
                  (btu/context-assoc! :user-id user-id)
                  (apply (get-task-action user-id) [])
                  (let [execution-time (int (/ (- (System/nanoTime) start) 1000000))]
                    (swap! current-tasks update task-id #(-> %
                                                             (dissoc :user-id)
                                                             (update :completed conj execution-time)))
                    (log/info "task <"))))))
          (catch InterruptedException e
            (.interrupt (Thread/currentThread))
            (log/info e "Simulator thread interrupted"))
          (catch Throwable t
            (log/error t "Internal error" (with-out-str
                                            (pprint (merge {::context @task-context}
                                                           (ex-data t))))))
          (finally
            (log/info "Simulator thread shutting down")
            (btu/stop-existing-driver!)
            (swap! current-tasks dissoc task-id)))))))

(mount/defstate simulator-thread-pool
  :start (Executors/newCachedThreadPool)
  :stop (when simulator-thread-pool
          (.shutdownNow simulator-thread-pool)
          (when-not (.awaitTermination simulator-thread-pool 5 TimeUnit/MINUTES)
            (throw (IllegalStateException. "did not terminate")))))

(defn start-simulator-threads! [{:keys [url concurrency]}]
  (let [startable (- concurrency (count @current-tasks))]
    (dotimes [_ startable]
      (.submit ^ExecutorService simulator-thread-pool
               ^Callable (create-task! url)))))

(defn print-simulator-statistics []
  (let [completed (mapcat :completed (vals @current-tasks))
        average-execution-time (when (seq completed)
                                 (int (/ (reduce + completed)
                                         (count completed))))]
    (log/info "statistics:"
              (format "active=%d, avg_execution_time=%dms"
                      (count @current-tasks) (or average-execution-time 0)))))

(mount/defstate queue-simulate-tasks
  :start (let [args (mount/args)]
           (log/info 'queue-simulate-tasks args)
           (start-simulator-threads! args)
           (scheduler/start! "simulate-tasks"
                             #(do (print-simulator-statistics)
                                  (start-simulator-threads! args))
                             (.toStandardDuration (time/seconds 30))))
  :stop (when queue-simulate-tasks
          (scheduler/stop! queue-simulate-tasks)))

(comment
  (-> {:url "http://localhost:3000" :concurrency 4}
      (mount/start-with-args #'queue-simulate-tasks #'simulator-thread-pool))
  (mount/stop #'queue-simulate-tasks #'simulator-thread-pool)

  @current-tasks
  (reset! current-tasks (list)))
