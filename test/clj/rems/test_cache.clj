(ns rems.test-cache
  (:require [clojure.pprint]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging.readable :as logr]
            [clojure.tools.logging.test :as log-test]
            [clojure.walk]
            [medley.core :refer [map-vals]]
            [rems.cache :as cache]
            [rems.common.dependency :as dep]
            [rems.common.util :refer [range-1]]
            [rems.concurrency :as concurrency]
            [rems.config]))

(defn- submit-all [thread-pool & fns]
  (->> fns
       (mapv bound-fn*)
       (concurrency/submit! thread-pool)))

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
                rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))
                rems.config/env {:dev true}]
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
          (is (= {:evict 0 :get 1 :reload 0 :upsert 0}
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
                  rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))
                  rems.config/env {:dev true}]
      (let [_cache-a (cache/basic {:id :a :depends-on [:b]})]
        (is (thrown-with-msg? RuntimeException #"Circular dependency between :b and :a"
                              (cache/basic {:id :b :depends-on [:a]}))))))

  (testing "existing cache id"
    (with-redefs [rems.cache/caches (doto caches (reset! nil))
                  rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))]

      (testing "only warning is logged in dev"
        (with-redefs [rems.config/env {:dev true}]
          (log-test/with-log
            (cache/basic {:id :a})
            (is (not (log-test/logged? "rems.cache" :warn "overriding cache id :a")))
            (cache/basic {:id :a})
            (is (log-test/logged? "rems.cache" :warn "overriding cache id :a")))))

      (testing "assertion error when not in dev"
        (with-redefs [rems.config/env {}]
          (is (thrown-with-msg? AssertionError #"Assert failed: error overriding cache id :a"
                                (cache/basic {:id :a})))))))

  (testing "can create basic caches with dependencies"
    (with-redefs [rems.cache/caches (doto caches (reset! nil))
                  rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))
                  rems.config/env {:dev true}]
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

(defmacro with-tracing [& body]
  `(let [start# (System/nanoTime)
         value# (do ~@body)
         end# (System/nanoTime)]
     [start# end# value#]))

(defn- random-wait-ms [& [extra]]
  (Thread/sleep (+ 1 (rand-int (or extra 5)))))

(defn- random-wait-ns []
  (Thread/sleep 0 (+ 0 (rand-int 2))))

(deftest test-cache-transact
  ;; Test approach:
  ;; - with two atoms representing the database
  ;; - with two caches
  ;; - with two dependent caches
  ;; - with four readers and two writers
  ;; - iterate 20 rounds of
  ;;   - writers write numbers to db and update cache
  ;;   - readers read caches and dependent caches
  ;;   - log each event
  ;; - wait for everything to finish
  ;; - then check that each cache saw the atom values in monotonically increasing order

  (with-redefs [rems.cache/caches (doto caches (reset! nil))
                rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))
                rems.config/env {:dev true}]
    (let [progress (atom 0)
          current-a (atom 1)
          current-b (atom 1)
          make-progress! #(swap! progress inc)
          finished? #(<= 20 @progress)

          ;; separate event logs try to minimize test latency
          reader-a-events (atom [])
          reader-a-event! #(swap! reader-a-events conj [%1 :cache-reader-a %2 %3])

          reader-b-events (atom [])
          reader-b-event! #(swap! reader-b-events conj [%1 :cache-reader-b %2 %3])

          reader-dependent-b-events (atom [])
          reader-dependent-b-event! #(swap! reader-dependent-b-events conj [%1 :cache-reader-dependent-b %2 %3])

          reader-dependent-ab-events (atom [])
          reader-dependent-ab-event! #(swap! reader-dependent-ab-events conj [%1 :cache-reader-dependent-ab %2 %3])

          writer-a-events (atom [])
          writer-a-event! #(swap! writer-a-events conj [%1 :cache-writer-a %2 %3])

          writer-b-events (atom [])
          writer-b-event! #(swap! writer-b-events conj [%1 :cache-writer-b %2 %3])

          evicter-events (atom [])
          evicter-event! #(swap! evicter-events conj [%1 :cache-evicter %2 %3])

          get-all-events #(concat @reader-a-events
                                  @reader-b-events
                                  @reader-dependent-b-events
                                  @reader-dependent-ab-events
                                  @writer-a-events
                                  @writer-b-events
                                  @evicter-events)

          cache-a (cache/basic {:id :a
                                :miss-fn (fn [id] (random-wait-ms) @current-a)
                                :reload-fn (fn [] (random-wait-ms) {:a @current-a})})
          cache-b (cache/basic {:id :b
                                :miss-fn (fn [id] (random-wait-ms) @current-b)
                                :reload-fn (fn [] (random-wait-ms) {:b @current-b})})
          dependent-b (cache/basic {:id :dependent-b
                                    :depends-on [:b]
                                    :reload-fn (fn [deps] (random-wait-ms) {:b (-> deps :b :b)})})
          dependent-ab (cache/basic {:id :dependent-ab
                                     :depends-on [:a :b]
                                     :reload-fn (fn [deps]
                                                  {:ab (+ (* 1000 (-> deps :a :a))
                                                          (-> deps :b :b))})})

          readers-finished? #(and (= @current-a (:a (nth (last @reader-a-events) 2)))
                                  (= @current-b (:b (nth (last @reader-b-events) 2)))
                                  (= @current-b (:b (nth (last @reader-dependent-b-events) 2)))
                                  (= (+ (* 1000 @current-a)
                                        @current-b)
                                     (:ab (nth (last @reader-dependent-ab-events) 2))))

          cache-transactions-thread-pool (concurrency/cached-thread-pool {:thread-prefix "test-cache-transactions"})]
      (try
        (logr/info "number of available processors:" (concurrency/get-available-processors))

        (submit-all cache-transactions-thread-pool
                    (fn cache-reader-a []
                      (while true
                        (random-wait-ms)

                        (let [[start end value] (with-tracing (cache/lookup-or-miss! cache-a :a))]
                          (reader-a-event! :lookup-or-miss
                                           {:a value}
                                           {:start start :end end}))))

                    (fn cache-reader-b []
                      (while true
                        (random-wait-ms)

                        (let [[start end value] (with-tracing (cache/lookup-or-miss! cache-b :b))]
                          (reader-b-event! :lookup-or-miss
                                           {:b value}
                                           {:start start :end end}))))

                    (fn cache-reader-dependent-b []
                      (while true
                        (random-wait-ms)

                        (let [[start end value] (with-tracing (cache/lookup! dependent-b :b))]
                          (reader-dependent-b-event! :lookup
                                                     {:b value}
                                                     {:start start :end end}))))

                    (fn cache-reader-dependent-ab []
                      (while true
                        (random-wait-ms)

                        (let [[start end value] (with-tracing (cache/lookup! dependent-ab :ab))]
                          (reader-dependent-ab-event! :lookup
                                                      {:ab value}
                                                      {:start start :end end}))))

                    (fn cache-writer-a []
                      (while (not (finished?))
                        (random-wait-ms)

                        (let [[start end value] (with-tracing
                                                  (swap! current-a inc)
                                                  (cache/miss! cache-a :a))]
                          (writer-a-event! :miss
                                           {:a value}
                                           {:start start :end end}))))

                    (fn cache-writer-b []
                      (while (not (finished?))
                        (random-wait-ms)
                        (let [[start end value] (with-tracing
                                                  (swap! current-b inc)
                                                  (cache/miss! cache-b :b))]
                          (writer-b-event! :miss
                                           {:b value}
                                           {:start start :end end}))))

                    (fn cache-evicter []
                      (while (not (finished?))
                        (random-wait-ms 50)
                        (let [[start end _] (with-tracing
                                              (cache/evict! cache-a :a))]
                          (evicter-event! :evict
                                          {}
                                          {:start start :end end})))))

        (while (not (finished?))
          (make-progress!)
          (println "progress" @progress)
          (Thread/sleep 50))

        (println "waiting for readers to finish")
        (let [start-time (System/currentTimeMillis)]
          (while (and (not (readers-finished?))
                      (< (- (System/currentTimeMillis) start-time) 1000))
            (Thread/sleep 50)))

        (finally
          (println "terminating test thread pool")
          (concurrency/shutdown-now! cache-transactions-thread-pool {:timeout-ms 5000})))

      (let [get-event-duration #(format "%.3fms" (/ (- (:end %) (:start %))
                                                    (* 1000.0 1000.0)))
            event-end (fn [[_ _ _ opts]] (:end opts))
            raw-events (->> (get-all-events)
                            (sort-by event-end)
                            (map (fn [[event-type id state-kv opts]]
                                   {:type event-type
                                    :id id
                                    :state state-kv
                                    :time (assoc opts :duration (get-event-duration opts))})))]

        (testing "all cache reads see monotonically growing results"
          (testing "reader a"
            (let [reader-a-seen (->> raw-events
                                     (filter (comp #{:cache-reader-a} :id))
                                     (mapv (comp :a :state))
                                     dedupe)]
              (is (apply < 0 reader-a-seen))))

          (testing "reader b"
            (let [reader-b-seen (->> raw-events
                                     (filter (comp #{:cache-reader-b} :id))
                                     (mapv (comp :b :state))
                                     dedupe)]
              (is (apply < 0 reader-b-seen))))

          (testing "reader dependent b"
            (let [reader-dependent-b-seen (->> raw-events
                                               (filter (comp #{:cache-reader-dependent-b} :id))
                                               (mapv (comp :b :state))
                                               dedupe)]
              (is (apply < 0 reader-dependent-b-seen))))

          (testing "reader dependent ab"
            (let [reader-dependent-ab-seen (->> raw-events
                                                (filter (comp #{:cache-reader-dependent-ab} :id))
                                                (mapv (comp :ab :state))
                                                dedupe)]
              (is (apply < 0 reader-dependent-ab-seen)))))))))

(defmacro capture-time [a id & body]
  `(let [start-time# (System/nanoTime)
         result# (do ~@body)
         end-time# (System/nanoTime)]
     (swap! ~a update ~id conj (- end-time# start-time#))
     result#))

(deftest test-cache-performance
  ;; Test approach:
  ;; - with two caches and two dependent caches
  ;; - with two writers and 100 readers,
  ;; - read & write 1000 times and
  ;; - print observed waiting times
  ;; - check end was reached
  (with-redefs [rems.cache/caches (doto caches (reset! nil))
                rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))
                rems.config/env {:dev true}]
    (let [current-a (atom 1)
          current-b (atom 1)

          cache-a (cache/basic {:id :a
                                :miss-fn (fn [id] (random-wait-ns) @current-a)
                                :reload-fn (fn [] (random-wait-ns) {:a @current-a})})
          cache-b (cache/basic {:id :b
                                :miss-fn (fn [id] (random-wait-ns) @current-b)
                                :reload-fn (fn [] (random-wait-ns) {:b @current-b})})
          dependent-b (cache/basic {:id :dependent-b
                                    :depends-on [:b]
                                    :reload-fn (fn [deps] (random-wait-ns) {:b (-> deps :b :b)})})
          dependent-ab (cache/basic {:id :dependent-ab
                                     :depends-on [:a :b]
                                     :reload-fn (fn [deps]
                                                  (random-wait-ns)
                                                  {:ab [(-> deps :a :a) (-> deps :b :b)]})})

          cache-performance-thread-pool (concurrency/cached-thread-pool {:thread-prefix "test-cache-performance"})

          humanize (fn [n]
                     (cond (> n 1000000)
                           (format "%.2f ms" (/ n 1000000.0))
                           (> n 1000)
                           (format "%.2f ms" (/ n 100000.0))
                           :else
                           (format "%.2f ns" (double n))))

          summarize (fn [coll]
                      {:min (humanize (apply min coll))
                       :max (humanize (apply max coll))
                       :avg (humanize (/ (reduce + 0 coll) (double (count coll))))})

          workers (apply submit-all
                         cache-performance-thread-pool
                         (concat [(fn cache-writer-a []
                                    (dotimes [i 1000]
                                      (reset! current-a i)
                                      (cache/miss! cache-a :a)))

                                  (fn cache-writer-b []
                                    (dotimes [i 1000]
                                      (reset! current-b i)
                                      (cache/miss! cache-b :b)))]

                                 (for [r (range-1 100)]
                                   (fn cache-reader []
                                     (let [time-a (atom {})]
                                       (doseq [i (shuffle (range 1000))]
                                         (random-wait-ns)
                                         (case (mod i 4)
                                           0 (capture-time time-a :a (cache/lookup-or-miss! cache-a :a))
                                           1 (capture-time time-a :b (cache/lookup-or-miss! cache-a :b))
                                           2 (capture-time time-a :da (cache/lookup! dependent-b :a))
                                           3 (capture-time time-a :dab (get (cache/entries! dependent-ab) :ab))))
                                       (assoc (map-vals summarize @time-a)
                                              :thread (format "%3d" r)))))))
          start-time (System/currentTimeMillis)]

      (try
        (println "waiting for workers to finish")
        (let [results (->> (for [w workers
                                 :let [result (.get ^java.util.concurrent.Future w)]]
                             (map-vals (fn [x]
                                         (if (:min x)
                                           (format "%9s < %9s < %9s" (:min x) (:avg x) (:max x))
                                           x))
                                       result))
                           (sort-by :thread)
                           doall)]

          (clojure.pprint/print-table [:thread :a :b :da :dab]
                                      results))

        (finally
          (println "terminating test thread pool")
          (concurrency/shutdown-now! cache-performance-thread-pool {:timeout-ms 5000})))

      (let [counts {:a @current-a
                    :b @current-b
                    :cache-a (cache/lookup! cache-a :a)
                    :cache-b (cache/lookup! cache-b :b)
                    :dependent-b (cache/lookup! dependent-b :b)
                    :dependent-ab (cache/lookup! dependent-ab :ab)}]

        (println "elapsed" (- (System/currentTimeMillis) start-time) "ms")

        (is (= {:a 999
                :b 999
                :cache-a 999
                :cache-b 999
                :dependent-b 999
                :dependent-ab [999 999]}
               counts))))))
