(ns rems.test-cache
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging.test :as log-test]
            [clojure.walk]
            [rems.cache :as cache]
            [rems.common.dependency :as dep]))

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

(deftest test-basic-cache
  (with-redefs [rems.cache/caches (doto caches (reset! nil))
                rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))]
    (let [cache-spy (atom nil)
          c (cache/basic {:id ::test-cache
                          :miss-fn (fn [id]
                                     (swap! cache-spy update-in [::test-cache :miss-fn] (fnil conj []) id)
                                     true)})]
      (is (= c (get @caches ::test-cache)))
      (is (= {} (get-cache-entries c)))
      (is (= [] (cache/get-cache-dependencies ::test-cache)))

      (testing "only lookup-or-miss! updates cache entry on cache miss"
        (is (= nil
               (cache/lookup! c :a)))
        (is (= nil
               @cache-spy))
        (is (= true
               (cache/lookup-or-miss! c :a)))
        (is (= {::test-cache {:miss-fn [:a]}}
               @cache-spy))
        (is (= true
               (cache/lookup! c :a)))))))

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
        (is (thrown-with-msg? AssertionError #"Assert failed: cache id :a already exists"
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
            (is (= [":reload :c"
                    ":reload :b"
                    ":reload :a"
                    ":reset-dependents :a {:dependents [:b :d]}" ; reset :d is not logged because it is already uninitialized
                    ":reload-finish :a {:count 1}"
                    ":reset-dependents :b {:dependents [:c :d]}"
                    ":reload-finish :b {:count 1}"
                    ":reload-finish :c {:count 1}"]
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
              (is (= [":reload :d"
                      ":reload-finish :d {:count 1}"]
                     (mapv :message (log-test/the-log)))))))))))
