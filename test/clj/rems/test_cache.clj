(ns rems.test-cache
  (:require [clj-time.core :as time]
            [clojure.pprint]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.tools.logging.test :as log-test]
            [clojure.walk]
            [medley.core :refer [assoc-some]]
            [mount.core :as mount]
            [rems.cache :as cache]
            [rems.common.dependency :as dep]
            [rems.concurrency :as concurrency]))

(mount/defstate cache-transactions-thread-pool
  :start (concurrency/cached-thread-pool {:thread-prefix "test-cache-transactions"})
  :stop (some-> cache-transactions-thread-pool
                (concurrency/stop! {:timeout-ms (time/in-millis (time/seconds 10))})))

(defn- submit-all [thread-pool & fns]
  (concurrency/submit! thread-pool fns))

(use-fixtures :each (fn [f]
                      (mount/start #'rems.cache/dependency-loaders #'cache-transactions-thread-pool)
                      (f)
                      (mount/stop #'rems.cache/dependency-loaders #'cache-transactions-thread-pool)))

(def ^:private caches (atom nil))
(def ^:private caches-dag (atom nil))

(defn- get-cache-entries
  "Returns cache as hash map that can be asserted with ="
  [c]
  (clojure.walk/keywordize-keys (cache/entries! c)))

(defn- get-cache-raw
  "Like get-cache-entries, but does not trigger cache readyness mechanisms."
  [c]
  (clojure.walk/keywordize-keys @(get c :the-cache)))

(def ^:private miss-fn (constantly {:value true}))
(def ^:private reload-fn (constantly {:always {:value :special}}))

(deftest test-basic-cache
  (with-redefs [rems.cache/caches (doto caches (reset! nil))
                rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))]
    (let [c (cache/basic {:id ::test-cache
                          :miss-fn (fn [id] (miss-fn id))
                          :reload-fn (fn [] (reload-fn))})]
      (is (= c
             (get @caches ::test-cache)))
      (is (= []
             (cache/get-cache-dependencies ::test-cache)))

      (testing "initialization"
        (testing "cache is uninitialized at start"
          (is (= {}
                 (get-cache-raw c)))
          (is (= false
                 (deref (:initialized? c))))
          (is (= {:evict 0 :get 0 :reload 0 :upsert 0}
                 (cache/export-statistics! c))))

        (testing "entries reloads cache"
          (is (= {:always {:value :special}}
                 (get-cache-entries c)))
          (is (= {:evict 0 :get 1 :reload 1 :upsert 0}
                 (cache/export-statistics! c)))))

      (testing "lookup"
        (is (= nil
               (cache/lookup! c :a)))
        (is (= {:value :special}
               (cache/lookup! c :always)))
        (is (= {:evict 0 :get 2 :reload 0 :upsert 0}
               (cache/export-statistics! c)))
        (is (= {:always {:value :special}}
               (get-cache-raw c))))

      (testing "lookup-or-miss"
        (testing "existing entry should not trigger cache miss"
          (is (= {:value :special}
                 (cache/lookup-or-miss! c :always)))
          (is (= {:evict 0 :get 2 :reload 0 :upsert 0}
                 (cache/export-statistics! c)))
          (is (= {:always {:value :special}}
                 (get-cache-raw c))))

        (testing "non-existing entry should be added on cache miss"
          (is (= {:value true}
                 (cache/lookup-or-miss! c :a)))
          (is (= {:evict 0 :get 2 :reload 0 :upsert 1}
                 (cache/export-statistics! c)))
          (is (= {:a {:value true}
                  :always {:value :special}}
                 (get-cache-raw c))))

        (testing "absent value skips cache entry"
          (with-redefs [miss-fn (constantly cache/absent)]
            (is (= nil
                   (cache/lookup-or-miss! c :test-skip))))
          (is (= {:evict 0 :get 1 :reload 0 :upsert 0}
                 (cache/export-statistics! c)))
          (is (= {:a {:value true}
                  :always {:value :special}}
                 (get-cache-raw c)))))

      (testing "evict"
        (cache/evict! c :a)
        (is (= nil
               (cache/lookup! c :a)))
        (is (= {:evict 1 :get 3 :reload 0 :upsert 0}
               (cache/export-statistics! c)))
        (is (= {:always {:value :special}}
               (get-cache-raw c)))

        (testing "non-existing entry does nothing"
          (cache/evict! c :does-not-exist)
          (is (= {:evict 0 :get 1 :reload 0 :upsert 0}
                 (cache/export-statistics! c)))
          (is (= {:always {:value :special}}
                 (get-cache-raw c)))))

      (testing "miss"
        (cache/miss! c :new-entry)
        (is (= {:value true}
               (cache/lookup! c :new-entry)))
        (is (= {:value true}
               (cache/lookup-or-miss! c :new-entry)))
        (is (= {:evict 0 :get 4 :reload 0 :upsert 1}
               (cache/export-statistics! c)))
        (is (= {:always {:value :special}
                :new-entry {:value true}}
               (get-cache-raw c)))))))

(deftest test-cache-dependencies
  (testing "cannot create caches with circular dependencies"
    (with-redefs [rems.cache/caches (doto caches (reset! nil))
                  rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))]
      (let [_cache-a (cache/basic {:id :a :depends-on [:b]})]
        (is (thrown-with-msg? RuntimeException #"Circular dependency between :b and :a"
                              (cache/basic {:id :b :depends-on [:a]}))))))

  (testing "cannot override existing cache id"
    (with-redefs [rems.cache/caches (doto caches (reset! nil))
                  rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))]
      (let [_cache-a (cache/basic {:id :a})]
        (is (thrown-with-msg? AssertionError #"Assert failed: error overriding cache id :a"
                              (cache/basic {:id :a}))))))

  (testing "can create basic caches with dependencies"
    (with-redefs [rems.cache/caches (doto caches (reset! nil))
                  rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))]
      (let [cache-a (cache/basic {:id :a
                                  :reload-fn (fn [] {1 true})})
            cache-b (cache/basic {:id :b
                                  :depends-on [:a]
                                  :reload-fn (fn [deps] {2 true})})
            cache-c (cache/basic {:id :c
                                  :depends-on [:b]
                                  :reload-fn (fn [deps] {3 true})})
            cache-d (cache/basic {:id :d
                                  :depends-on [:a :b]
                                  :reload-fn (fn [deps] {4 true})})]
        (testing "cache :a has no dependencies"
          (is (= cache-a (get @caches :a)))
          (is (= [] (cache/get-cache-dependencies :a))))

        (testing "cache :b depends on [:a]"
          (is (= cache-b (get @caches :b)))
          (is (= [cache-a] (cache/get-cache-dependencies :b))))

        (testing "cache :c depends on [:b]"
          (is (= cache-c (get @caches :c)))
          (is (= [cache-b] (cache/get-cache-dependencies :c))))

        (testing "cache :d depends on [:a :b]"
          (is (= cache-d (get @caches :d)))
          (is (= #{cache-a cache-b} (set (cache/get-cache-dependencies :d)))))

        (testing "accessing cache :c causes [:a :b] to reload"
          (log-test/with-log
            (is (= {}
                   (get-cache-raw cache-a)
                   (get-cache-raw cache-b)
                   (get-cache-raw cache-c)
                   (get-cache-raw cache-d))
                "raw caches should be empty initially")
            (is (= []
                   (log-test/the-log)))
            (is (= {3 true}
                   (get-cache-entries cache-c)))
            (is (= ["> :c :reload"
                    "> :b :reload"
                    "> :a :reload"
                    "> :a :reset-dependents {:dependents (:c :b :d)}"
                    "< :a :reset-dependents"
                    "< :a :reload {:count 1}"
                    "> :b :reset-dependents {:dependents (:c :d)}"
                    "< :b :reset-dependents"
                    "< :b :reload {:count 1}"
                    "< :c :reload {:count 1}"]
                   (mapv :message (log-test/the-log)))))

          (testing "accessing cache :a does not cause further reloads"
            (log-test/with-log
              (is (= {1 true}
                     (get-cache-entries cache-a)))
              (is (= []
                     (log-test/the-log)))))

          (testing "accessing cache :b does not cause further reloads"
            (log-test/with-log
              (is (= {2 true}
                     (get-cache-entries cache-b)))
              (is (= []
                     (log-test/the-log)))))

          (testing "accessing cache :c does not cause further reloads"
            (log-test/with-log
              (is (= {3 true}
                     (get-cache-entries cache-c)))
              (is (= []
                     (log-test/the-log)))))

          (testing "accessing cache :d reloads only itself"
            (log-test/with-log
              (is (= {4 true}
                     (get-cache-entries cache-d)))
              (is (= ["> :d :reload"
                      "< :d :reload {:count 1}"]
                     (mapv :message (log-test/the-log)))))))))))

(defmacro with-timing [& body]
  `(let [start# (System/nanoTime)
         value# (do ~@body)
         end# (System/nanoTime)]
     [start# end# value#]))

(defn- random-wait []
  (Thread/sleep (+ 1 (rand-int 3))))

(deftest test-cache-transactions
  (with-redefs [rems.cache/caches (doto caches (reset! nil))
                rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))]
    (let [;; worker writes to current-a
          ;;   cache a reads from current-a
          ;;   cache a resets dependent-a and dependent-c
          current-a (atom 0)
          ;; worker writes to current-b
          ;;   cache b reads from current-b
          ;;   cache b resets dependent-b
          current-b (atom 0)
          ;; separate event logs to minimize test latency on event ordering
          reader-a-events (atom [])
          reader-b-events (atom [])
          reader-c-events (atom [])
          writer-a-events (atom [])
          writer-b-events (atom [])
          resetter-events (atom [])

          cache-a (cache/basic {:id :a
                                :miss-fn (fn [_id]
                                           (let [value @current-a]
                                             (random-wait)
                                             value))
                                :reload-fn (fn []
                                             (let [value @current-a]
                                               (random-wait)
                                               {:a value}))})
          cache-b (cache/basic {:id :b
                                :miss-fn (fn [_id]
                                           (let [value @current-b]
                                             (random-wait)
                                             value))
                                :reload-fn (fn []
                                             (let [value @current-b]
                                               (random-wait)
                                               {:b value}))})
          ;; worker reads from dependent-a
          ;;   dependent a reads from cache a
          dependent-a (cache/basic {:id :dependent-a
                                    :depends-on [:a]
                                    :reload-fn (fn [deps]
                                                 (:a deps))})
          ;; worker reads from dependent-b
          ;;   dependent-b reads from cache-b
          dependent-b (cache/basic {:id :dependent-b
                                    :depends-on [:b]
                                    :reload-fn (fn [deps]
                                                 (:b deps))})
          ;; worker reads from dependent-c
          ;;   dependent c reads from cache a and cache b
          dependent-c (cache/basic {:id :dependent-c
                                    :depends-on [:a :b]
                                    :reload-fn (fn [deps]
                                                 (merge (:a deps) (:b deps)))})
          progress (atom 0)
          finished? #(<= 20 @progress)
          make-progress! #(swap! progress inc)]
      (try
        (submit-all cache-transactions-thread-pool
                    (fn cache-reader-a [] (while true
                                            (random-wait)
                                            (let [[start end value] (with-timing (cache/lookup! dependent-a :a))]
                                              (swap! reader-a-events conj [:lookup :cache-reader-a {:a value
                                                                                                    :start start
                                                                                                    :end end}]))))
                    (fn cache-reader-b [] (while true
                                            (random-wait)
                                            (let [[start end value] (with-timing (cache/lookup! dependent-b :b))]
                                              (swap! reader-b-events conj [:lookup :cache-reader-b {:b value
                                                                                                    :start start
                                                                                                    :end end}]))))
                    (fn cache-reader-c [] (while true
                                            (random-wait)
                                            (let [[start end value] (with-timing (cache/entries! dependent-c))]
                                              (swap! reader-c-events conj [:lookup :cache-reader-c (merge value
                                                                                                          {:start start
                                                                                                           :end end})]))))
                    (fn cache-writer-a [] (while (not (finished?))
                                            (random-wait)
                                            (let [expected (swap! current-a inc)
                                                  [start end value] (with-timing (cache/miss! cache-a :a))]
                                              (swap! writer-a-events conj [:update :cache-writer-a {:a value
                                                                                                    :expected expected
                                                                                                    :start start
                                                                                                    :end end}]))))
                    (fn cache-writer-b [] (while (not (finished?))
                                            (random-wait)
                                            (let [expected (swap! current-b inc)
                                                  [start end value] (with-timing (cache/miss! cache-b :b))]
                                              (swap! writer-b-events conj [:update :cache-writer-b {:b value
                                                                                                    :expected expected
                                                                                                    :start start
                                                                                                    :end end}]))))
                    (fn cache-resetter [] (while (not (finished?))
                                            (Thread/sleep 200)
                                            (let [[start end] (with-timing (run! cache/set-uninitialized! [cache-a
                                                                                                           cache-b
                                                                                                           dependent-a
                                                                                                           dependent-b
                                                                                                           dependent-c]))]
                                              (swap! resetter-events conj [:reset :cache-resetter {:start start
                                                                                                   :end end}])))))
        (while (not (finished?))
          (make-progress!)
          (println "progress" @progress)
          (Thread/sleep 50))
        (finally
          (println "terminating test thread pool")
          (mount/stop #'cache-transactions-thread-pool)
          (mount/stop #'rems.cache/dependency-loaders)))

      (let [raw-events (->> (concat @reader-a-events
                                    @reader-b-events
                                    @reader-c-events
                                    @writer-a-events
                                    @writer-b-events
                                    @resetter-events)
                            (map (fn [[event-type id arg-map]]
                                   [event-type id (-> arg-map
                                                      (dissoc :start)
                                                      (assoc :duration (format "%.3fms"
                                                                               (/ (- (:end arg-map) (:start arg-map))
                                                                                  (* 1000.0 1000.0)))))]))
                            (sort-by (fn [[_event-type _id arg-map]]
                                       (:end arg-map))))
            result (->> raw-events
                        (reduce (fn [m [event-type id arg-map]]
                                  (let [state (dissoc m :read-count :reads :writes)]
                                    (case event-type
                                      ;; when upstream state is committed to cache
                                      :update (-> m
                                                  (assoc-some :a (:a arg-map)
                                                              :b (:b arg-map))
                                                  (update :writes conj (assoc (select-keys arg-map [:a :b :expected])
                                                                              :id id
                                                                              :state state)))
                                      ;; when cache is read from
                                      :lookup (-> m
                                                  (update-in [:read-count id] (fnil inc 0))
                                                  (update :reads conj (assoc (select-keys arg-map [:a :b])
                                                                             :id id
                                                                             :state state)))
                                      m)))
                                {:a 0 :b 0 :reads [] :writes []}))
            valid-read-event? (fn [{:keys [a b id state]}]
                                (case id
                                  :cache-reader-a (= (:a state) a)
                                  :cache-reader-b (= (:b state) b)
                                  :cache-reader-c (and (= (:a state) a)
                                                       (= (:b state) b))))
            valid-write-event? (fn [{:keys [a b expected id state]}]
                                 (case id
                                   ;; updates must be sequential
                                   :cache-writer-a (= a expected (inc (:a state)))
                                   :cache-writer-b (= b expected (inc (:b state)))))]
        ;; for local debugging
        #_(do
            (spit "debug-cache-transactions-test-raw-events.edn"
                  (binding [clojure.pprint/*print-right-margin* 150]
                    (with-out-str
                      (clojure.pprint/pprint raw-events))))
            (spit "debug-cache-transactions-test.edn"
                  (binding [clojure.pprint/*print-right-margin* 150]
                    (with-out-str
                      (clojure.pprint/pprint result)))))
        (testing "no deadlock"
          (is (< 1 (:a result)))
          (is (< 1 (:b result)))
          (is (< 1 (get-in result [:read-count :cache-reader-a] 0)))
          (is (< 1 (get-in result [:read-count :cache-reader-b] 0)))
          (is (< 1 (get-in result [:read-count :cache-reader-c] 0))))

        (testing "all cache writes happen in correct order"
          (is (= [] (remove valid-write-event? (:writes result)))))

        (testing "all cache reads happen in correct order"
          (is (= [] (remove valid-read-event? (:reads result)))))))))
