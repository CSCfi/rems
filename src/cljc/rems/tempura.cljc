(ns rems.tempura
  (:require [clojure.string]
            [clojure.test :refer [deftest is testing]]
            [clojure.walk]
            [taoensso.tempura]))

;; backtick (`) is used to escape parameter (%)
(def +vector-args+ #"(?<!`)%\d")
(def +map-args+ #"(?<!`)%:([^\s`%]+)%")

(defn- replace-map-args [resource]
  (let [res-keys (atom {})
        idx (atom 0)
        upsert! (fn [k]
                  (when-not (contains? @res-keys k)
                    (swap! res-keys assoc k (swap! idx inc)))
                  (get @res-keys k))
        index! (fn [match]
                 (let [map-arg (keyword (second match))
                       vec-arg (upsert! map-arg)]
                   (str "%" vec-arg)))
        resource (->> resource
                      (clojure.walk/postwalk #(if (string? %)
                                                (clojure.string/replace % +map-args+ index!)
                                                %)))]
    {:resource resource
     :resource-keys (->> (sort-by val @res-keys)
                         (mapv key))}))

(deftest test-replace-map-args
  (testing "string transformation"
    (is (= {:resource "{:x %1 :y %2}"
            :resource-keys [:x :y]}
           (replace-map-args "{:x %:x% :y %:y%}"))))
  (testing "hiccup transformation"
    (is (= {:resource [:div {:aria-label "argument x is %1, argument y is %2"} "{:x %1 :y %2}"]
            :resource-keys [:x :y]}
           (replace-map-args [:div {:aria-label "argument x is %:x%, argument y is %:y%"} "{:x %:x% :y %:y%}"])))))

(defn- string-seq [x]
  (filter string? (tree-seq vector? seq x)))

(defn find-vec-params [x]
  (mapcat #(re-seq +vector-args+ %) (string-seq x)))

(defn find-map-params [x]
  (->> (string-seq x)
       (mapcat #(re-seq +map-args+ %))
       (map (comp keyword second))))

(deftest test-find-params
  (testing "vec params"
    (is (= ["%1" "%2" "%1" "%2" "%3"]
           (find-vec-params "arg %1, arg %2, args %1 %2 %3")
           (find-vec-params [:div "arg %1" [:span "arg %2"] [:span "args %1 %2 %3"]])))
    (is (= [] (find-vec-params (constantly "arg %1, arg %2, args %1 %2 %3"))))
    (is (= [] (find-vec-params nil))))
  (testing "map params"
    (is (= [:x :long.ns/y :x :long.ns/y :z]
           (find-map-params "arg %:x%, arg %:long.ns/y%, args %:x% %:long.ns/y% %:z%")
           (find-map-params [:div "arg %:x%" [:span "arg %:long.ns/y%"] [:span "args %:x% %:long.ns/y% %:z%"]])))
    (is (= [] (find-map-params (constantly "arg %:x%, arg %:long.ns/y%, args %:x% %:long.ns/y% %:z%"))))
    (is (= [] (find-map-params nil)))))

(def ^:private get-default-resource-compiler (:resource-compiler taoensso.tempura/default-tr-opts))

(defn- get-vec-compiler [resource]
  (let [f (get-default-resource-compiler resource)]
    (fn compile-vec-args [vargs]
      (let [res-args (if (map? (first vargs))
                       (vec (rest vargs))
                       vargs)]
        (f res-args)))))

(defn- get-map-compiler [resource-with-map-params]
  (let [{:keys [resource resource-keys]} (replace-map-args resource-with-map-params)
        f (get-default-resource-compiler resource)]
    (fn compile-map-args [vargs]
      (f (cond
           (empty? vargs) []
           (map? (first vargs)) (mapv (first vargs) resource-keys)
           :else (assert (map? (first vargs))
                         {:resource resource-with-map-params
                          :resource-keys resource-keys
                          :vargs vargs}))))))

(defn- get-resource-compiler [resource]
  (if (seq (find-map-params resource))
    (get-map-compiler resource)
    (get-vec-compiler resource)))

(def default-tr-opts {:cache-dict? :fn-local
                      :cache-locales? :fn-local})

(defn get-cached-tr [translations & [opts]]
  (taoensso.tempura/new-tr-fn (-> default-tr-opts
                                  (merge opts)
                                  (assoc :dict translations
                                         :resource-compiler (memoize get-resource-compiler)))))

(deftest test-get-cached-tr
  (let [dict {:en
              {:string {:no-args "test"
                        :vector "%1 %2 %1"
                        :map "%:x% %:y% %:x%"}
               :hiccup {:no-args [:div {:aria-label "test"} [:span "test"]]
                        :vector [:div {:aria-label "%1 %2 %1"} [:span "%1 %2 %1"]]
                        :map [:div {:aria-label "%:x% %:y% %:x%"} [:span "%:x% %:y% %:x%"]]}}}
        tr (get-cached-tr dict)]
    (testing "no args"
      (is (= "test"
             (tr [:en] [:string/no-args])))
      (is (= [:div {:aria-label "test"} [:span "test"]]
             (tr [:en] [:hiccup/no-args]))))
    (testing "index parameters"
      (is (= "1 2 1"
             (tr [:en] [:string/vector] [1 2 3])))
      ;; map attributes are identified by custom resource compiler, but tempura does not use them
      (is (= [:div {:aria-label "%1 %2 %1"} [:span "" 1 " " 2 " " 1]]
             (tr [:en] [:hiccup/vector] [1 2 3]))))
    (testing "named parameters"
      (is (= "1 2 1"
             (tr [:en] [:string/map] [{:x 1 :y 2 :z 3}])))
      ;; map attributes are identified by custom resource compiler, but tempura does not use them
      (is (= [:div {:aria-label "%1 %2 %1"} [:span "" 1 " " 2 " " 1]]
             (tr [:en] [:hiccup/map] [{:x 1 :y 2 :z 3}]))))))
