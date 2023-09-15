(ns rems.tempura
  (:require [clojure.string]
            [clojure.test :refer [deftest is testing]]
            [clojure.walk]
            #?(:cljs [re-frame.core :as rf])
            #?@(:clj [[rems.context :as context]
                      [rems.locales]])
            [taoensso.tempura]))

;; backtick (`) is used to escape vector argument (%)
(defonce ^:private +map-args+ #"(?<!`)%:([^\s`%]+)%")
(defonce ^:private +vector-args+ #"(?<!`)%\d")

(deftest test-map-args
  (is (= ["key" "my.special/namespace?"]
         (->> "%:key% for (%:my.special/namespace?%) `%:ignored% %:also-ignored`%"
              (re-seq +map-args+)
              (mapv last)))))

(defn- replace-map-args [resource]
  (let [res-keys (atom {})
        idx (atom 0)
        upsert! (fn [k]
                  (when-not (contains? @res-keys k)
                    (swap! res-keys assoc k (swap! idx inc)))
                  (get @res-keys k))
        resource (->> resource
                      (clojure.walk/postwalk
                       (fn [node]
                         (if (string? node)
                           (clojure.string/replace node +map-args+ #(let [map-arg (keyword (second %))
                                                                          vec-arg (upsert! map-arg)]
                                                                      (str "%" vec-arg)))
                           node))))]
    {:resource resource
     :resource-keys (->> (sort-by val @res-keys)
                         (mapv key))}))

(def ^:private memoized-replace-map-args (memoize replace-map-args))

(deftest test-replace-map-args
  (testing "string transformation"
    (is (= {:resource "{:x %1 :y %2}"
            :resource-keys [:x :y]}
           (replace-map-args "{:x %:x% :y %:y%}"))))
  (testing "memoized"
    (let [memoized-f (memoize replace-map-args)]
      (is (= {:resource "{:x %1 :y %2}"
              :resource-keys [:x :y]}
             (memoized-f "{:x %:x% :y %:y%}")
             (memoized-f "{:x %:x% :y %:y%}")))))
  (testing "hiccup transformation"
    (is (= {:resource [:div {:aria-label "argument x is %1, argument y is %2"} "{:x %1 :y %2}"]
            :resource-keys [:x :y]}
           (replace-map-args [:div {:aria-label "argument x is %:x%, argument y is %:y%"} "{:x %:x% :y %:y%}"])))))

(def ^:private get-resource-compiler (:resource-compiler taoensso.tempura/default-tr-opts))

(defn- compile-vec-args [resource vargs]
  (let [compile-vargs (get-resource-compiler resource)]
    (compile-vargs (vec vargs))))

(defn- compile-map-args [resource arg-map]
  (let [res-map (memoized-replace-map-args resource)]
    (compile-vec-args (:resource res-map)
                      (map arg-map (:resource-keys res-map)))))

(defn- tempura-config []
  {:dict #?(:clj rems.locales/translations
            :cljs @(rf/subscribe [:translations]))
   :resource-compiler (fn [resource]
                        (fn [vargs]
                          (cond
                            (map? (first vargs)) (if (re-find +vector-args+ resource)
                                                   (compile-vec-args resource (rest vargs))
                                                   (compile-map-args resource (first vargs)))
                            :else (compile-vec-args resource vargs))))})

(defn get-language []
  #?(:clj context/*lang*
     :cljs @(rf/subscribe [:language])))

(defn tr
  "When translation function is called with both map and vector arguments,
   custom resource compiler can use either argument format for translation.
   Argument formats cannot be mixed. When using both argument formats,
   map argument must be given first followed by vector arguments:

   (tr [:key] [{:k :v} x1 x2])"
  ([ks args] (taoensso.tempura/tr (tempura-config)
                                  [(get-language)]
                                  (vec ks)
                                  (vec args)))
  ([ks] (taoensso.tempura/tr (tempura-config)
                             [(get-language)]
                             (vec ks))))
