(ns rems.tempura
  (:require [better-cond.core :as b]
            [clojure.string]
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

(defn find-map-params [resource]
  (b/cond
    :let [extract-args #(->> (re-seq +map-args+ %)
                             (map second))]

    (string? resource) (set (extract-args resource))
    (vector? resource) (set (->> (flatten resource)
                                 (filter string?)
                                 (mapcat extract-args)))
    nil))

(deftest test-find-map-params
  (is (= #{"x" "long.ns/y" "z"}
         (find-map-params "arg %:x%, arg %:long.ns/y%, args %:x% %:long.ns/y% %:z%")
         (find-map-params [:div "arg %:x%" [:span "arg %:long.ns/y%"] [:span "args %:x% %:long.ns/y% %:z%"]])))
  (is (nil? (find-map-params (constantly "arg %:x%, arg %:long.ns/y%, args %:x% %:long.ns/y% %:z%"))))
  (is (nil? (find-map-params nil))))

(def ^:private get-default-resource-compiler (:resource-compiler taoensso.tempura/default-tr-opts))

(defn- get-vec-compiler [resource]
  (let [f (get-default-resource-compiler resource)]
    (fn compile-vec-args [vargs]
      (let [res-args (if (map? (first vargs))
                       (vec (rest vargs))
                       vargs)]
        (f res-args)))))

(defn- get-map-compiler [{:keys [resource resource-keys]}]
  (let [f (get-default-resource-compiler resource)]
    (fn compile-map-args [vargs]
      (assert (map? (first vargs)) {:resource resource
                                    :vargs vargs})
      (let [res-args (mapv (first vargs) resource-keys)]
        (f res-args)))))

(defn- get-resource-compiler [resource]
  (if (seq (find-map-params resource))
    (get-map-compiler (replace-map-args resource))
    (get-vec-compiler resource)))

(defn tr
  "Wrapper for `taoensso.tempura/tr`."
  ([translations language ks] (taoensso.tempura/tr {:dict translations}
                                                   [language]
                                                   (vec ks)))
  ([translations language ks args] (taoensso.tempura/tr {:dict translations
                                                         :resource-compiler get-resource-compiler}
                                                        [language]
                                                        (vec ks)
                                                        (vec args))))

(deftest test-resource-compiler
  (let [dict {:en
              {:string {:no-args "test"
                        :vector "%1 %2 %1"
                        :map "%:x% %:y% %:x%"}
               :hiccup {:no-args [:div {:aria-label "test"} [:span "test"]]
                        :vector [:div {:aria-label "%1 %2 %1"} [:span "%1 %2 %1"]]
                        :map [:div {:aria-label "%:x% %:y% %:x%"} [:span "%:x% %:y% %:x%"]]}}}]
    (testing "no args"
      (is (= "test"
             (tr dict :en [:string/no-args])))
      (is (= [:div {:aria-label "test"} [:span "test"]]
             (tr dict :en [:hiccup/no-args]))))
    (testing "index parameters"
      (is (= "1 2 1"
             (tr dict :en [:string/vector] [1 2 3])))
      ;; map attributes are identified by custom resource compiler, but tempura does not use them
      (is (= [:div {:aria-label "%1 %2 %1"} [:span "" 1 " " 2 " " 1]]
             (tr dict :en [:hiccup/vector] [1 2 3]))))
    (testing "named parameters"
      (is (= "1 2 1"
             (tr dict :en [:string/map] [{:x 1 :y 2 :z 3}])))
      ;; map attributes are identified by custom resource compiler, but tempura does not use them
      (is (= [:div {:aria-label "%1 %2 %1"} [:span "" 1 " " 2 " " 1]]
             (tr dict :en [:hiccup/map] [{:x 1 :y 2 :z 3}]))))))
