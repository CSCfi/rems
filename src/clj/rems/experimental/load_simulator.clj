(ns rems.experimental.load-simulator
  "Run load simulator from REMS. Simulator is configured with CLI options,
   and can be run standalone or using REPL. Functions are provided for simulating
   concurrent users via headless webdriver using etaoin."
  (:require [clj-time.core :as time]
            [clojure.pprint :refer [pprint]]
            [clojure.set]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [rems.browser-test-util :as btu]
            [rems.common.util :refer [getx parse-int]]
            [rems.db.applications]
            [rems.logging :refer [with-mdc]]
            [rems.scheduler :as scheduler]
            [rems.service.test-data :as test-data]
            [rems.test-browser :as b]
            [rems.experimental.simulator-util :as simu]
            [rems.util :refer [rand-nth*]])
  (:import [java.util.concurrent Executors ExecutorService TimeUnit]))

(def ^:private task-counter (atom 0))
(def ^:private current-tasks (atom {}))
(def ^:private task-statistics (atom {:completed []
                                      :failed 0}))

(defn submit-new-application []
  (simu/with-test-browser
    (simu/login-as (btu/context-get :user-id))
    (btu/context-assoc! :cat-item (simu/get-random-catalogue-item))
    (b/go-to-catalogue)
    (b/add-to-cart (btu/context-get :cat-item))
    (b/click-cart-apply)
    (btu/context-assoc! :application-id (parse-int (b/get-application-id)))
    (simu/fill-application-fields (btu/context-get :application-id))
    (Thread/sleep 2000) ; wait for ui to catch up
    (when (btu/exists? :accept-licenses-button)
      (b/accept-licenses))
    (b/send-application)
    (b/logout)))

;; XXX: add more actions, e.g. view pdf, return/approve/reject
(defn handle-application []
  (simu/with-test-browser
    (simu/login-as (btu/context-get :user-id))
    (btu/context-assoc! :application-id (-> (btu/context-get :user-id)
                                            (simu/get-random-todo-application)
                                            :application/id))
    (b/go-to-application (btu/context-get :application-id))
    (btu/scroll-and-click :remark-action-button)
    (let [selector {:id (b/get-field-id "Add remark")}]
      (btu/fill-human selector (test-data/random-long-string 5)))
    (btu/scroll-and-click :remark)
    (btu/wait-visible :status-success)
    (b/logout)))

(defn view-application []
  (simu/with-test-browser
    (simu/login-as (btu/context-get :user-id))
    (b/go-to-applications)
    (-> (btu/context-get :user-id)
        (simu/get-random-application)
        :application/id
        (b/go-to-application))
    (b/logout)))

(comment
  ; convenience for development
  (btu/init-driver! :chrome "http://localhost:3000/" :development)

  (btu/context-assoc! :user-id "alice")
  (view-application)

  (btu/context-assoc! :user-id "handler")
  (handle-application)

  (btu/context-assoc! :user-id "alice")
  (with-redefs [simu/get-random-catalogue-item (constantly "Default workflow 2")]
    (submit-new-application)))

(defn get-task-action [user-id]
  (let [roles (rems.db.applications/get-all-application-roles user-id)
        actions (case (rand-nth* roles)
                  :handler [handle-application]
                  [submit-new-application view-application])]
    (rand-nth* actions)))

(defn get-task-user! [task-id]
  (locking current-tasks
    (let [current-users (map :user-id (vals @current-tasks))
          available-users (-> (simu/get-all-users)
                              (clojure.set/difference (set current-users)))]
      (when-some [user-id (rand-nth* available-users)]
        (swap! current-tasks assoc-in [task-id :user-id] user-id)
        user-id))))

(defn create-task! [url]
  (let [task-id (swap! task-counter inc)
        task-context (atom (assoc (btu/create-base-context)
                                  :url url
                                  :seed "simulator"
                                  :task-id task-id))]
    (swap! current-tasks assoc task-id {})
    (fn simulate []
      (log/info "Simulator thread starting")
      (binding [btu/*test-context* task-context]
        (try
          (btu/init-driver! :chrome (btu/get-server-url))
          (while true
            (let [start (System/nanoTime)]
              (when-some [user-id (get-task-user! task-id)]
                (btu/context-assoc! :user-id user-id)
                (with-mdc {:userid user-id}
                  (log/debug "task >")
                  (apply (get-task-action user-id) [])
                  (let [execution-time (int (/ (- (System/nanoTime) start) 1000000))]
                    (swap! current-tasks update task-id dissoc :user-id)
                    (swap! task-statistics update :completed conj execution-time))
                  (log/debug "task <")))))
          (catch InterruptedException e
            (.interrupt (Thread/currentThread))
            (log/info e "Simulator thread interrupted"))
          (catch Throwable t
            (log/error t "Internal error" (with-out-str
                                            (pprint (merge {::context @task-context}
                                                           (ex-data t)))))
            (swap! task-statistics update :failed inc))
          (finally
            (log/info "Simulator thread shutting down")
            (btu/stop-existing-driver!)
            (swap! current-tasks dissoc task-id)))))))

(defn validate [opts]
  (when-some [simulator (:simulator opts)]
    {:url (getx simulator :url)
     :concurrency (getx simulator :concurrency)}))

(mount/defstate simulator-thread-pool
  :start (when (validate (mount/args))
           (Executors/newCachedThreadPool))
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
  (let [completed (:completed @task-statistics)
        completed-count (count completed)
        failed-count (:failed @task-statistics)
        average-execution-time (when (pos? completed-count)
                                 (int (/ (reduce + completed)
                                         completed-count)))]
    (log/info "statistics:"
              (format "active_threads=%d, completed_tasks=%d, failed_tasks=%d, task_avg_execution_time=%dms"
                      (count @current-tasks) completed-count failed-count (or average-execution-time 0)))))

(mount/defstate queue-simulate-tasks
  :start (when-some [opts (validate (mount/args))]
           (log/info 'queue-simulate-tasks opts)
           (start-simulator-threads! opts)
           (scheduler/start! "simulate-tasks"
                             #(do (print-simulator-statistics)
                                  (start-simulator-threads! opts))
                             (.toStandardDuration (time/seconds 15))))
  :stop (when queue-simulate-tasks
          (scheduler/stop! queue-simulate-tasks)))

