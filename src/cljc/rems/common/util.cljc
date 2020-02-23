(ns rems.common.util
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [medley.core :refer [update-existing]]))

;; TODO remove separate clj and cljs implementations of getx and getx-in
(defn getx
  "Like `get` but throws an exception if the key is not found."
  [m k]
  (let [e (get m k ::sentinel)]
    (if-not (= e ::sentinel)
      e
      (throw (ex-info "Missing required key" {:map m :key k})))))

(defn getx-in
  "Like `get-in` but throws an exception if the key is not found."
  [m ks]
  (reduce getx m ks))

(defn getcat-in
  "Select a seq of values from a nested structure.

  Like `clojure.core/get-in` and `clojure.core/mapcat` combined."
  [m ks]
  (if (empty? ks)
    [m]
    (let [n (get m (first ks))]
      (if (map? n)
        (getcat-in n (rest ks))
        (mapcat #(getcat-in % (rest ks)) n)))))

(deftest test-getcat-in
  (is (= [1 2 3 4 5 6]
         (getcat-in {:foo {:bar [{:baz [1 2 3]}
                                 {:baz [4 5 6]}]}}
                    [:foo :bar :baz]))))

(defn update-each
  "Updates each item in a nested map or collection structure with `f`, item and `args`.

  Like `clojure.core/update` except
  - maps `f` for each item in collections
  - maps `f` regularly for `clojure.core/map?`
  - only updates if key exists."
  [m k f & args]
  (update-existing m k (fn [coll]
                         (mapv #(apply f % args)
                               coll))))

(deftest test-update-each
  (is (= nil (update-each nil :foo str)))
  (is (= {} (update-each {} :coll str)) "only existing keys are mapped")
  (is (= {:coll ["0" "1" "2"]}
         (update-each {:coll (range 3)}
                      :coll
                      str)))
  (is (= {:coll [2 3 4]}
         (update-each {:coll (range 3)}
                      :coll
                      + 2))))

(defn update-in-each
  "Updates each item in a nested map or collection structure with `f`, item and `args`.

  Like `clojure.core/update-in` except
  - maps `f` for each item in collections
  - maps `f` regularly for `clojure.core/map?`
  - only updates if key exists."
  [m ks f & args]
  (if (empty? ks)
    (apply f m args)
    (if (map? (get m (first ks)))
      (update-existing m (first ks) #(apply update-in-each % (rest ks) f args))
      (update-each m (first ks) #(apply update-in-each % (rest ks) f args)))))

(deftest test-update-in-each
  (is (= nil (update-in-each nil [:foo] str)))
  (is (= {} (update-in-each {} [:coll] str)) "only existing keys are mapped")
  (is (= {:coll ["0" "1" "2"]}
         (update-in-each {:coll (range 3)}
                         [:coll]
                         str)))
  (is (= {:coll [{:range ["0" "1" "2"]}
                 {:range ["0" "1"]}]}
         (update-in-each {:coll [{:range (range 3)}
                                 {:range (range 2)}]}
                         [:coll :range]
                         str)))
  (is (= {:coll [{:map {:map [{:range ["0" "1" "2"]}
                              {:range ["0" "1"]}]}}]}
         (update-in-each {:coll [{:map {:map [{:range (range 3)}
                                              {:range (range 2)}]}}]}
                         [:coll :map :map :range]
                         str)))
  (is (= {:coll [3 4 5]}
         (update-in-each {:coll (range 3)}
                         [:coll]
                         + 1 2))))

(defn select-vals
  "Select values in map `m` specified by given keys `ks`.

  Values will be returned in the order specified by `ks`.

  You can specify a `default-value` that will be used if the
  key is not found in the map. This is like `get` function."
  [m ks & [default-value]]
  (vec (reduce #(conj %1 (get m %2 default-value)) [] ks)))

(defn index-by
  "Index the collection coll with given keys `ks`.
  Result is a nested map, `(count ks)` levels deep, e.g.

    (index-by [:a :b] [{:a 1 :b \"x\"} {:a 1 :b \"y\"}])
      ==> {1 {\"x\" {:a 1 :b \"x\"}
              \"y\" {:a 1 :b \"y\"}}}

  In case of non-unique keys, index-by picks the first value, e.g.

    (index-by [:a] [{:a 1 :b \"x\"} {:a 1 :b \"y\"}])
      ==> {1 {:a 1 :b \"x\"}}"
  [ks coll]
  (if (empty? ks)
    (first coll)
    (->> coll
         (group-by (first ks))
         (map (fn [[k v]] [k (index-by (rest ks) v)]))
         (into {}))))

(deftest test-index-by
  (is (= {1 {"x" {:a 1 :b "x"}
             "y" {:a 1 :b "y"}}}
         (index-by [:a :b] [{:a 1 :b "x"} {:a 1 :b "y"}])))
  (is (= {false 1 true 2}
         (index-by [even?] [1 2 3 4]))))

(defn distinct-by
  "Remove duplicates from sequence, comparing the value returned by key-fn.
   The first element that key-fn returns a given value for is retained.

   Order of sequence is not preserved in any way."
  [key-fn sequence]
  (map first (vals (group-by key-fn sequence))))

(defn andstr
  "Like `apply str coll` but only produces something if all the
  values are truthy like with `and`.

  Useful for statements like
  ```clj
  (str (andstr (:foo x) \"/\") (:bar y))
  ```
  See also `test-andstr` for examples."
  [& coll]
  (when (every? identity coll)
    (apply str coll)))

(deftest test-andstr
  (testing "when any argument is falsey the result is nil"
    (is (= nil (andstr nil 1 2 3)))
    (is (= nil (andstr 1 2 false 3))))
  (testing "when all arguments are truthy the results are concatenated"
    (let [x {:foo 2}]
      (is (= "2/" (andstr (:foo x) "/")))
      (is (= "(2)" (andstr "(" (:foo x) ")"))))))

(defn deep-merge
  "Recursively merges maps and sequentials so that the values in `b`
  will replace the values at the same key or index in `a`."
  [a b]
  (cond (and (sequential? a) (sequential? b))
        (let [max-length (max (count a) (count b))
              a (take max-length (concat a (repeat nil)))
              b (take max-length (concat b (repeat nil)))]
          (doall (map deep-merge a b)))

        (map? a)
        (merge-with deep-merge a b)

        :else b))

(deftest test-deep-merge
  (testing "merge nil"
    (is (= nil
           (deep-merge nil
                       nil)))
    (is (= {:a 1}
           (deep-merge nil
                       {:a 1})))
    (is (= {:a 1}
           (deep-merge {:a 1}
                       nil))))
  (testing "preserve false"
    (is (= {:b false}
           (deep-merge {:b :anything}
                       {:b false}))))
  (testing "merge maps"
    (is (= {:a 2}
           (deep-merge {:a 1}
                       {:a 2})))
    (is (= {:a 1 :b 2 :c 2}
           (deep-merge {:a 1 :b 1}
                       {:b 2 :c 2})))
    (is (= {:a {:b {:c 100 :d 2}}}
           (deep-merge {:a {:b {:c 1 :d 2}}}
                       {:a {:b {:c 100}}}))))
  (testing "merge vectors"
    (is (= [{:a 2}]
           (deep-merge [{:a 1}]
                       [{:a 2}])))
    (is (= [{:a 1 :b 2 :c 2}]
           (deep-merge [{:a 1 :b 1}]
                       [{:b 2 :c 2}])))
    (is (= [{:a 1} {:b 2}]
           (deep-merge [{:a 1}]
                       [nil {:b 2}])))
    (is (= [{:b 2} {:a 1}]
           (deep-merge [nil {:a 1}]
                       [{:b 2}]))))
  (testing "merge lists"
    (is (= [{:a 2}]
           (deep-merge '({:a 1})
                       '({:a 2}))))
    (is (= [{:a 1 :b 2 :c 2}]
           (deep-merge '({:a 1 :b 1})
                       '({:b 2 :c 2}))))))

(defn recursive-keys [m]
  (mapcat (fn [[k v]]
            (if (map? v)
              (map (partial cons k) (recursive-keys v))
              [(list k)]))
          m))

(deftest test-recursive-keys
  (is (= [[:a] [:b]] (recursive-keys {:a [1] :b "foo"})))
  (is (= [[:a :b] [:a :c] [:a :d :e] [:a :d :f]]
         (recursive-keys {:a {:b 1 :c nil :d {:e "foo" :f [3]}}}))))

(defn parse-int [s]
  #?(:clj (try
            (when s
              (java.lang.Integer/parseInt s))
            (catch NumberFormatException e
              nil))
     :cljs (let [x (js/parseInt s)]
             (when-not (js/isNaN x)
               x))))


(deftest test-parse-int
  (is (= nil (parse-int nil)))
  (is (= nil (parse-int "")))
  (is (= nil (parse-int "a")))
  (is (= 7 (parse-int "7"))))

(defn remove-empty-keys
  "Given a map, recursively remove keys with empty map or nil values.

  E.g., given {:a {:b {:c nil} :d {:e :f}}}, return {:a {:d {:e :f}}}."
  [m]
  (into {} (filter (fn [[_ v]] (not ((if (map? v) empty? nil?) v)))
                   (mapv (fn [[k v]] [k (if (map? v)
                                          (remove-empty-keys v)
                                          v)])
                         m))))

(deftest test-remove-empty-keys
  (is (= (remove-empty-keys {}) {}))
  (is (= (remove-empty-keys {:a :b}) {:a :b}))
  (is (= (remove-empty-keys {:a nil}) {}))
  (is (= (remove-empty-keys {:a {:b {:c nil} :d {:e :f}}}) {:a {:d {:e :f}}})))

(defn normalize-file-path
  "A file path may contain local filesystem parts that we want to remove
  so that we can use the path to refer to e.g. project GitHub."
  [path]
  (str/replace (subs path (str/index-of path "src"))
               "\\" "/"))

(deftest normalize-file-path-test
  (is (= "src/foo/bar.clj" (normalize-file-path "/home/john/rems/src/foo/bar.clj")))
  (is (= "src/foo/bar.clj" (normalize-file-path "C:\\Users\\john\\rems\\src\\foo/bar.clj"))))
