(ns rems.common.util
  (:require [medley.core :refer [map-keys map-vals remove-keys]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

;; regex from https://developer.mozilla.org/en-US/docs/Web/HTML/Element/input/email#Validation
(def +email-regex+ #"[a-zA-Z0-9.!#$%&'*+\/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*")

(def +phone-number-regex+ #"^\+[1-9][0-9\s]{4,26}$")

(deftest test-phone-number-regex
  (is (= "+358450000000"
         (re-matches +phone-number-regex+ "+358450000000")))
  (is (= nil
         (re-matches +phone-number-regex+ "+058450000000")))
  (is (= "+3 5 8 4 5 0 0 0 0 0 0 0 "
         (re-matches +phone-number-regex+ "+3 5 8 4 5 0 0 0 0 0 0 0 ")))
  (is (= nil
         (re-matches +phone-number-regex+ "+35845000000000000000000000000000"))))

;; regex from https://stackoverflow.com/questions/5284147/validating-ipv4-addresses-with-regexp
(def +ipv4-regex+ #"(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")

(def +ipv6-regex+ #"(([0-9]|[a-f]){1,4}(\:)){7}(([0-9]|[a-f]){1,4})$")

;; https://stackoverflow.com/questions/2814002/private-ip-address-identifier-in-regular-expression
;; for now, this test should test for the following private IPv4 address pattaerns
;; 0.x.x.x
;; 10.x.x.x - Private network, local communications
;; 100.64.x.x–100.127.x.x - Private network, communications between a service provider and its subscribers
;; 127.x.x.x - host, loopback addresses to the local host
;; 169.254.x.x - Subnet, Used for link-local addresses[7] between two hosts on a single link when no IP address is otherwise specified
;; 172.16.x.x – 172.31.x.x - Private network, local communications within a private network
;; 192.0.0.x - Private network, IETF Protocol Assignments
;; 192.0.2.x - Documentation, Assigned as TEST-NET-1, documentation and examples.
;; 192.168.x.x - Private network, local communications within a private network
;; 198.18.x.x - 198.19.x.x - Private network, benchmark testing of inter-network communications between two separate subnets
;; 198.51.100.x - Documentation, Assigned as TEST-NET-2, documentation and examples.
;; 203.0.113.x - Documentation, Assigned as TEST-NET-3, documentation and examples.
;; 224.x.x.x - In use for IP multicast.[11] (Former Class D network).
;; 240.x.x.x–255.x.x.x - multicast
(def +reserved-ipv4-range-regex+
  (re-pattern
   (str
    "((0|10|127|224)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$)|"
    "(100\\.(6[4-9]|[7-9][0-9]|1[0-1][0-7]|12[0-7])\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$)|"
    "(169\\.254\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$)|"
    "(172\\.(1[6-9]|2[0-9]|3[0-1])\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$)|"
    "(192\\.0\\.(0|2?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$)|"
    "(192\\.168\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$)|"
    "(198\\.1[8-9]\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$)|"
    "(198\\.51\\.100\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$)|"
    "(203\\.0\\.113\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$)|"
    "((2[4-5][0-9])\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$)")))

;; https://simpledns.plus/private-ipv6
;; https://serverfault.com/questions/546606/what-are-the-ipv6-public-and-private-and-reserved-ranges
(def +reserved-ipv6-range-regex+
  (re-pattern
   (str "(fdce|fc00|fd00)\\:(([0-9]|[a-f]){1,4}(\\:)){6}(([0-9]|[a-f]){1,4})$")))

(deftest test-ip-address-regex
  (is (= "0.0.0.0"
         (first (re-matches +ipv4-regex+ "0.0.0.0"))))
  (is (= "255.255.255.255"
         (first (re-matches +ipv4-regex+ "255.255.255.255"))))
  (is (= "127.0.0.1"
         (first (re-matches +ipv4-regex+ "127.0.0.1"))))
  (is (= "192.168.0.0"
         (first (re-matches +ipv4-regex+ "192.168.0.0"))))
  (is (= "142.250.74.110"
         (first (re-matches +ipv4-regex+ "142.250.74.110"))))
  (is (= "10.255.255.255"
         (first (re-matches +ipv4-regex+ "10.255.255.255"))))
  (is (= nil
         (first (re-matches +ipv4-regex+ "256.255.255.255"))))
  (is (= nil
         (first (re-matches +ipv4-regex+ "10.foo.bar.255"))))
  ;; 10.x.x.x - Private network, local communications
  (is (= "10.0.0.0"
         (first (re-matches +reserved-ipv4-range-regex+ "10.0.0.0"))))
  (is (= "10.26.167.0"
         (first (re-matches +reserved-ipv4-range-regex+ "10.26.167.0"))))
  (is (= "10.0.255.255"
         (first (re-matches +reserved-ipv4-range-regex+ "10.0.255.255"))))
  (is (= "192.0.0.255"
         (first (re-matches +reserved-ipv4-range-regex+ "192.0.0.255"))))
  (is (= "192.0.2.255"
         (first (re-matches +reserved-ipv4-range-regex+ "192.0.2.255"))))
  (is (= "192.168.10.255"
         (first (re-matches +reserved-ipv4-range-regex+ "192.168.10.255"))))
  (is (= "172.16.0.255"
         (first (re-matches +reserved-ipv4-range-regex+ "172.16.0.255"))))

  ;; 100.64.x.x–100.127.x.x - Private network, communications between a service provider and its subscribers
  (is (= "100.71.0.255"
         (first (re-matches +reserved-ipv4-range-regex+ "100.71.0.255"))))
  (is (= "100.64.0.255"
         (first (re-matches +reserved-ipv4-range-regex+ "100.64.0.255"))))
  (is (= "100.100.0.255"
         (first (re-matches +reserved-ipv4-range-regex+ "100.100.0.255"))))
  (is (= "100.127.78.10"
         (first (re-matches +reserved-ipv4-range-regex+ "100.127.78.10"))))
  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "100.255.78.10"))))
  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "100.198.78.10"))))

  ;; 169.254.x.x - Subnet, Used for link-local addresses[7] between two hosts on a single link when no IP address is otherwise specified
  (is (= "169.254.1.1"
         (first (re-matches +reserved-ipv4-range-regex+ "169.254.1.1"))))
  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "16.254.1.1"))))
  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "169.25.1.1"))))
  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "169.25.1.1"))))

  ;; 172.16.x.x – 172.31.x.x - Private network, local communications within a private network
  (is (= "172.16.0.0"
         (first (re-matches +reserved-ipv4-range-regex+ "172.16.0.0"))))
  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "172.1.0.0"))))
  (is (= "172.30.0.0"
         (first (re-matches +reserved-ipv4-range-regex+ "172.30.0.0"))))
  (is (= "172.31.0.0"
         (first (re-matches +reserved-ipv4-range-regex+ "172.31.0.0"))))
  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "172.11.0.0"))))

  ;; 192.0.0.x - Private network, IETF Protocol Assignments
  ;; 192.0.2.x - Documentation, Assigned as TEST-NET-1, documentation and examples.
  ;; 192.168.x.x - Private network, local communications within a private network

  (is (= "192.0.0.0"
         (first (re-matches +reserved-ipv4-range-regex+ "192.0.0.0"))))
  (is (= "192.0.0.189"
         (first (re-matches +reserved-ipv4-range-regex+ "192.0.0.189"))))
  (is (= "192.0.2.189"
         (first (re-matches +reserved-ipv4-range-regex+ "192.0.2.189"))))
  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "192.0.25.189"))))
  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "192.0.1.10"))))
  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "19.0.1.10"))))
  (is (= "192.168.1.10"
         (first (re-matches +reserved-ipv4-range-regex+ "192.168.1.10"))))
  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "192.16.1.10"))))

  ;; 198.18.x.x - 198.19.x.x - Private network, benchmark testing of inter-network communications between two separate subnets
  ;; 198.51.100.x - Documentation, Assigned as TEST-NET-2, documentation and examples.

  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "198.1.100.78"))))
  (is (= "198.18.100.78"
         (first (re-matches +reserved-ipv4-range-regex+ "198.18.100.78"))))
  (is (= "198.51.100.78"
         (first (re-matches +reserved-ipv4-range-regex+ "198.51.100.78"))))
  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "198.50.100.78"))))

  ;; 203.0.113.x - Documentation, Assigned as TEST-NET-3, documentation and examples.
  (is (= "203.0.113.89"
         (first (re-matches +reserved-ipv4-range-regex+ "203.0.113.89"))))
  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "203.0.111.89"))))

  (is (= nil
         (first (re-matches +reserved-ipv4-range-regex+ "142.250.74.110"))))

  (is (= "2001:db8:1870:999:128:7648:3849:688"
         (first (re-matches +ipv6-regex+ "2001:db8:1870:999:128:7648:3849:688"))))
  (is (= nil
         (first (re-matches +ipv6-regex+ "2001:db8:1g70:999:128:7648:3849:688"))))
  (is (= nil
         (first (re-matches +reserved-ipv6-range-regex+ "2001:db8:1g70:999:128:7648:3849:688"))))
  (is (= "fdce:de09:d25d:b23e:de8:d0e8:de8:de8"
         (first (re-matches +reserved-ipv6-range-regex+ "fdce:de09:d25d:b23e:de8:d0e8:de8:de8")))))

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

(def conj-set (fnil conj #{}))

(def conj-vec (fnil conj []))

(def into-vec (fnil into []))

(defn select-vals
  "Select values in map `m` specified by given keys `ks`.

  Values will be returned in the order specified by `ks`.

  You can specify a `default-value` that will be used if the
  key is not found in the map. This is like `get` function."
  [m ks & [default-value]]
  (vec (reduce #(conj %1 (get m %2 default-value)) [] ks)))

(defn build-index
  "Index the `coll` with given keys `:keys` and map values with given
  `:value-fn` (defaults to `identity`).

  Results is nested map, `(count keys)` levels deep, e.g.
    (build-index {:keys [:a :b] :value-fn :c}
                 [{:a 1 :b \"x\" :c :a} {:a 1 :b \"y\" :c :b}])
      ==> {1 {\"x\" :a
              \"y\" :b}}

  In case of non-unique keys, `build-index` picks the first value, e.g.

    (build-index {:keys [:a]} [{:a 1 :b \"x\"} {:a 1 :b \"y\"}])
      ==> {1 {:a 1 :b \"x\"}}

  You can override this behaviour by passing in a `:collect-fn`, which
  is applied to the sequence of values. The default `:collect-fn` is
  `first`."
  [{key-seq :keys
    value-fn :value-fn
    collect-fn :collect-fn}
   coll]
  (if-let [[k & ks] (seq key-seq)]
    (->> coll
         (group-by k)
         (map-vals #(build-index {:keys ks
                                  :value-fn value-fn
                                  :collect-fn collect-fn} %)))
    ((or collect-fn first) (map (or value-fn identity) coll))))

(deftest test-build-index
  (testing "unique keys"
    (is (= {1 {"x" :a "y" :b}}
           (build-index {:keys [:a :b] :value-fn :c}
                        [{:a 1 :b "x" :c :a} {:a 1 :b "y" :c :b}])))
    (is (= {"x" {1 :a} "y" {1 :b}}
           (build-index {:keys [:b :a] :value-fn :c}
                        [{:a 1 :b "x" :c :a} {:a 1 :b "y" :c :b}]))))
  (testing "non-unique keys"
    (is (= {:a 1} (build-index {:keys []}
                               [{:a 1} {:b 2}])))
    (is (= {:b 2} (build-index {:keys [] :collect-fn second}
                               [{:a 1} {:b 2}])))
    (is (= #{{:a 1} {:b 2}} (build-index {:keys [] :collect-fn set}
                                         [{:a 1} {:b 2}])))
    (is (= {1 #{10 11} 2 #{10}}
           (build-index {:keys [:a] :value-fn :c :collect-fn set}
                        [{:a 1 :c 10} {:a 1 :c 11} {:a 2 :c 10}])))))

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
  (build-index {:keys ks} coll))

(deftest test-index-by
  (is (= {1 {"x" {:a 1 :b "x"}
             "y" {:a 1 :b "y"}}}
         (index-by [:a :b] [{:a 1 :b "x"} {:a 1 :b "y"}])))
  (is (= {false 1 true 2}
         (index-by [even?] [1 2 3 4]))))

(defn build-dags
  "Builds a tree out of `coll` with a parent having children `:children-fn`
  and maps values with given `:value-fn`.

  The identity of a node must be unique, but it can appear in several places of the tree
  as long as it does not have any cycles. Cycles will cause the nodes not to appear in the result.

  `:id-fn`           - gives the identity of a value
  `:child-id-fn`     - gives the identity of a child
  `:children-fn`     - gives the children of a tree node, single or seq value allowed
  `:set-children-fn` - sets the children of a tree node
  `:value-fn`        - the value of a tree node, defaults to `identity`
  `:filter-fn`       - function called to check should the node be included, defaults to `(constantly true)`

  Results is nested map, e.g.
    (build-dags {:id-fn :a
                 :children-fn :c}
                 [{:a 1} {:a 2 :c [1]}])
      ==> {:a 2 :c [{:a 1}]}"
  [{:keys [id-fn child-id-fn children-fn set-children-fn value-fn filter-fn]
    :or {child-id-fn identity
         set-children-fn (fn [node children]
                           (if (seq children)
                             (assoc node children-fn children)
                             (dissoc node children-fn)))
         value-fn identity
         filter-fn (constantly true)}}
   coll]
  (let [children-set (set (for [x coll
                                child (children-fn x)
                                :when child]
                            (child-id-fn child)))
        roots (remove (comp children-set id-fn) coll)
        node-by-id (index-by [id-fn] coll)]
    (letfn [(expand-node [node]
              (set-children-fn (value-fn node)
                               (->> node
                                    children-fn
                                    (map (comp node-by-id child-id-fn))
                                    (remove nil?)
                                    (filter filter-fn)
                                    (mapv (comp value-fn expand-node)))))]
      (->> roots
           (map expand-node)
           (filter filter-fn)
           vec))))

(deftest test-build-dags
  (is (= [{:id 3
           :children [{:id 1
                       :unrelated "x"}
                      {:id 2
                       :unrelated "z"
                       :children [{:id 1
                                   :unrelated "x"}]}]}
          {:id 4
           :unrelated "y"}]
         (build-dags {:id-fn :id
                      :children-fn :children}
                     [{:id 1 :unrelated "x"}
                      {:id 2 :unrelated "z" :children [1]}
                      {:id 3 :children [1 2]}
                      {:id 4 :unrelated "y"}])))

  (is (= [{:id 3
           :children [{:id 1
                       :unrelated "x"}
                      {:id 2
                       :unrelated "z"
                       :children [{:id 1
                                   :unrelated "x"}]}]}
          {:id 4}]
         (build-dags {:id-fn :id
                      :child-id-fn :id
                      :children-fn :children}
                     [{:id 1 :unrelated "x"}
                      {:id 2 :unrelated "z" :children [{:id 1}]}
                      {:id 3 :children [{:id 1} {:id 2}]}
                      {:id 4 :children [{:id :not-found}]}])))

  (testing "filtering"
    (is (= [{:id 3
             :children [{:id 1
                         :unrelated "x"}]}]
           (build-dags {:id-fn :id
                        :children-fn :children
                        :filter-fn (comp odd? :id)}
                       [{:id 1 :unrelated "x"}
                        {:id 2 :unrelated "z" :children [1]}
                        {:id 3 :children [1 2]}
                        {:id 4 :unrelated "y"}]))))

  (testing "cyclic data"
    (is (= [{:id 5 :unrelated "survives"}]
           (build-dags {:id-fn :id
                        :child-id-fn :id
                        :children-fn :children}
                       [{:id 1 :unrelated "x" :children [{:id 3}]}
                        {:id 2 :unrelated "y" :children [{:id 1}]}
                        {:id 3 :unrelated "z" :children [{:id 2} {:id 4}]}
                        {:id 4 :unrelated "is destroyed in a cycle"}
                        {:id 5 :unrelated "survives"}])))))

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
  (apply set/union
         (for [[k v] m]
           (if (map? v)
             (set (map (partial cons k) (recursive-keys v)))
             #{(list k)}))))

(deftest test-recursive-keys
  (is (= #{[:a] [:b]} (recursive-keys {:a [1] :b "foo"})))
  (is (= #{[:a :b] [:a :c] [:a :d :e] [:a :d :f]}
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

(defn clamp
  "Clamps `x` to be between `low` and `high` inclusive."
  [x low high]
  (-> x
      (min high)
      (max low)))

(deftest test-clamp
  (is (= 0 (clamp -1 0 1)))
  (is (= 0 (clamp 1 0 0)))
  (is (= 1 (clamp 2 0 1)))
  (is (= 0 (clamp 0 0 1))))

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
  (if-let [index (if path (str/index-of path "src") 0)]
    (some-> path
            (subs index)
            (str/replace "\\" "/"))
    path))

(deftest normalize-file-path-test
  (is (= "src/foo/bar.clj" (normalize-file-path "/home/john/rems/src/foo/bar.clj")))
  (is (= "src/foo/bar.clj" (normalize-file-path "C:\\Users\\john\\rems\\src\\foo/bar.clj"))))

(defn add-postfix [filename postfix]
  (if-let [i (str/last-index-of filename \.)]
    (str (subs filename 0 i) postfix (subs filename i))
    (str filename postfix)))

(deftest test-add-postfix
  (is (= "foo (1).txt"
         (add-postfix "foo.txt" " (1)")))
  (is (= "foo_bar_quux (1)"
         (add-postfix "foo_bar_quux" " (1)")))
  (is (= "foo.bar!.quux"
         (add-postfix "foo.bar.quux" "!")))
  (is (= "!"
         (add-postfix "" "!"))))

(defn fix-filename [filename existing-filenames]
  (let [exists? (set existing-filenames)
        versions (cons filename
                       (map #(add-postfix filename (str " (" (inc %) ")"))
                            (range)))]
    (first (remove exists? versions))))

(deftest test-fix-filename
  (is (= "file.txt"
         (fix-filename "file.txt" ["file.pdf" "picture.gif"])))
  (is (= "file (1).txt"
         (fix-filename "file.txt" ["file.txt" "boing.txt"])))
  (is (= "file (2).txt"
         (fix-filename "file.txt" ["file.txt" "file (1).txt" "file (3).txt"]))))

(defn assoc-some-in
  "Like `clojure.core/assoc-in`, but only associates value `v` in key path
   `ks` of associate collection `m` when `(true? (some? v)))`."
  [m ks v]
  (if (some? v)
    (assoc-in m ks v)
    m))

(deftest test-assoc-some-in
  (is (= (assoc-some-in {} [:a] 1) {:a 1}))
  (is (= (assoc-some-in {} [:a :b] 1) {:a {:b 1}}))
  (is (= (assoc-some-in {:a {:b 1}} [:a :b] 2) {:a {:b 2}}))
  (is (= (assoc-some-in {:a {:b 1}} [:a :b] nil) {:a {:b 1}})))

(defn replace-key
  "Replaces key `k1` with key `k2` in map `m`.
   If `k1` does not exist in `m`, returns `m`.

   **Examples:**
   ```clojure
   (replace-key {:a 1} :a :b)
   ;;=> {:b 1}

   (replace-key {:a 1} :b :c)
   ;;=> {:a 1}
   ```"
  [m k1 k2]
  (if (contains? m k1)
    (-> (assoc m k2 (get m k1))
        (dissoc k1))
    m))

(deftest test-replace-key
  (is (= {} (replace-key {} :a :b)))
  (is (= nil (replace-key nil :a :b)))
  (is (= [] (replace-key [] :a :b)))
  (is (= {:b 1} (replace-key {:a 1} :a :b)))
  (is (= {:a 1} (replace-key {:a 1} :b :c))))

(defn update-present
  "Like clojure.core/update, but does nothing if the key `k` does not exist in `m`."
  [m k f & args]
  (if (find m k)
    (apply update m k f args)
    m))

(deftest test-update-present
  (is (= {:a 1} (update-present {:a 1} :b (constantly true))))
  (is (= {:a 1 :b true} (update-present {:a 1 :b 2} :b (constantly true))))
  (is (= {:a 1 :b true} (update-present {:a 1 :b nil} :b (constantly true)))))

(defn assoc-not-present
  "Like `clojure.core/assoc` but only assocs values into `m` if the key is not already present in `m`.

  Good for setting default values."
  [m & kvs]
  (reduce (fn [m [k v]]
            (if (contains? m k)
              m
              (assoc m k v)))
          m
          (partition 2 kvs)))

(deftest test-assoc-not-present
  (is (= {:a 1} (assoc-not-present {} :a 1)))
  (is (= {:a 1 :b 2} (assoc-not-present {:b 2} :a 1)))
  (is (= {:a 1} (assoc-not-present {:a 1} :a 2))
      "doesn't replace existing key")
  (is (= {:a 1 :b 2 :c 3 :d 4} (assoc-not-present {:a 1 :c 3} :a 2 :b 2 :c 4 :d 4))
      "complex example"))

;; https://developer.mozilla.org/en-US/docs/Web/HTML/Global_attributes/id
(defn escape-element-id
  "Replaces non-conforming characters from string `id` for the purpose
   of creating an identifier suitable for HTML element id."
  [id]
  (when (string? id)
    (-> id
        (str/replace #"[^A-Za-z0-9\-\_]" "_") ; "only ASCII letters, digits, '_', and '-' should be used,"
        (str/replace-first #"^[^A-Za-z]" #(str "id_" %))))) ; "and the value for an id attribute should start with a letter"

(deftest test-escape-element-id
  (testing "should replace element special characters with underscore"
    (is (nil? (escape-element-id nil)))
    (is (= "element_id-123_" (escape-element-id "element id-123 ")))
    (is (= "element_id_123" (escape-element-id "element.id#123")))
    (is (= "id_123-element" (escape-element-id "123-element")))
    (is (= "id__123-element" (escape-element-id " 123-element")))))

(defn keep-keys
  "Takes a sequence of associative collections and maps function `f`
   over keys of each, returning a new associative collection with
   non-nil keys. Useful for renaming and selecting only a subset of
   associative collection keys."
  [f coll]
  (->> coll
       (map (partial map-keys f))
       (map (partial remove-keys nil?))))

(deftest test-keep-keys
  (is (= [] (keep-keys {} [])))
  (is (= [{:b 1}] (keep-keys {:a :b} [{:a 1}])))
  (is (= [{:b 1} {:c 2}] (keep-keys {:a :b :b :c} [{:a 1} {:b 2}]))))

(defn contains-all-kv-pairs? [supermap map]
  (set/superset? (set supermap) (set map)))

(defn apply-filters [filters coll]
  (let [filters (or filters {})]
    (filter #(contains-all-kv-pairs? % filters) coll)))

