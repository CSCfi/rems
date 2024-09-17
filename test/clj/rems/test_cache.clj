(ns rems.test-cache
  (:require [clojure.test :refer [deftest is testing]]
            [rems.cache :as cache]
            [rems.common.dependency :as dep]))

(def ^:private caches (atom nil))
(def ^:private caches-dag (atom nil))

(deftest test-cache-dependencies
  (testing "can create basic cache with no dependencies"
    (with-redefs [rems.cache/caches (doto caches (reset! nil))
                  rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))]
      (let [c (cache/basic {:id ::test-cache})]
        (is (= c
               (get @caches ::test-cache)))
        (is (= []
               (cache/get-cache-dependencies ::test-cache))))))
  (testing "can create basic caches that depend on other caches"
    (with-redefs [rems.cache/caches (doto caches (reset! nil))
                  rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))]
      (let [cache-a (cache/basic {:id ::a})
            cache-b (cache/basic {:id ::b :depends-on [::a]})
            cache-c (cache/basic {:id ::c :depends-on [::b]})]
        (testing "cache a has no dependencies"
          (is (= cache-a
                 (get @caches ::a)))
          (is (= []
                 (cache/get-cache-dependencies ::a))))
        (testing "cache b depends on cache a"
          (is (= cache-b
                 (get @caches ::b)))
          (is (= [cache-a]
                 (cache/get-cache-dependencies ::b))))
        (testing "cache c depends on cache b"
          (is (= cache-c
                 (get @caches ::c)))
          (is (= [cache-b]
                 (cache/get-cache-dependencies ::c)))))))
  (testing "cannot create caches with circular dependencies"
    (with-redefs [rems.cache/caches (doto caches (reset! nil))
                  rems.cache/caches-dag (doto caches-dag (reset! (dep/make-graph)))]
      (let [_cache-a (cache/basic {:id :a :depends-on [:b]})]
        (is (thrown-with-msg? RuntimeException #"Circular dependency between :b and :a"
                              (cache/basic {:id :b :depends-on [:a]})))))))
