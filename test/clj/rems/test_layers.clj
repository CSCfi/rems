(ns rems.test-layers
  "Test REMS layer architecture automatically.

  Generates an architecture diagram.

  NB: requires `graphviz` and `clj-kondo` to be installed on the machine.
  NB: Work in progress. You should run `graph` manually currently."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.logging]
            [clojure.test :refer [deftest is]]
            [medley.core :refer [index-by]]
            [tangle.core]))


(defn interesting-ns? [s]
  (and (str/starts-with? s "rems.")
       (not (str/ends-with? s ".util"))
       (not (str/starts-with? s "rems.api.schema"))
       (not (str/starts-with? s "rems.service.dependencies"))
       (or (str/starts-with? s "rems.ext.")
           ;;(str/starts-with? s "rems.cli")
           (str/starts-with? s "rems.db.")
           (str/starts-with? s "rems.service.")
           (str/starts-with? s "rems.api"))))

;; use for future namespace renaming needs
(defn rename-ns [s]
  (-> s
      #_(str/replace "rems.db.test-data" "rems.service.test-data")))

(defn ok-transition? [from to]
  (case [(:layer from) (:layer to)]
    [:api :service] true
    [:service :db] true
    [:service :service] true
    [:api :ext] true
    [:service :ext] true
    [:ext :ext] true
    [:db :db] (= "rems.db.core" (:name to))
    [:api :api] (= "rems.api" (:name from))
    false))
(defn analyze-code []
  (let [ret (sh/sh "clj-kondo" "--lint" "src" "--parallel" "--config" "{:output {:format :edn} :analysis true}")
        analysis (-> ret :out edn/read-string :analysis)
        namespaces (->> analysis
                        :namespace-definitions
                        (mapv (comp str :name))
                        ;;(concat ["rems.cli"]) ; consider implementing `rems.cli` ns
                        #_(mapv rename-ns)
                        (filter interesting-ns?)
                        (mapv #(merge {:name %}
                                      (cond (str/starts-with? % "rems.ext.") {:layer :ext}
                                            (str/starts-with? % "rems.db.") {:layer :db}
                                            (str/starts-with? % "rems.service.") {:layer :service}
                                            (str/starts-with? % "rems.api") {:layer :api}))))

        namespace-by-id (index-by :name namespaces)

        namespace-usages (->> analysis
                              :namespace-usages
                              (map (juxt (comp str :from) (comp str :to)))
                              distinct
                              #_(map (fn [[from to]] [(rename-ns from) (rename-ns to)]))
                              (filterv (fn [[from to]]
                                         (and (interesting-ns? from)
                                              (interesting-ns? to))))
                              ;;(concat [["rems.cli" "rems.service.test-data"]])
                              (map (fn [[from to]] [from to (when-not (ok-transition? (namespace-by-id from)
                                                                                      (namespace-by-id to))
                                                              {:color :red
                                                               :constraint false
                                                               :weight 0.01})])))]
    {:namespace-usages namespace-usages
     :namespaces namespaces}))

(defn graph []
  (let [{nodes :namespaces edges :namespace-usages} (analyze-code)
        dot (tangle.core/graph->dot nodes edges {:directed? true
                                                 :graph {:rankdir :LR
                                                         :ranksep 3.5
                                                         :rank :min
                                                         :dpi 150
                                                         :layout :dot}
                                                 :edge {:penwidth 2}
                                                 :node {:shape :box}
                                                 :node->id :name
                                                 :node->descriptor (fn [node]
                                                                     (when-let [layer (:layer node)]
                                                                       (case layer
                                                                         :api {:style :filled
                                                                               :fontcolor :white
                                                                               :fillcolor "red"}
                                                                         :service {:style :filled
                                                                                   :fillcolor "#00ff00"}
                                                                         :db {:style :filled
                                                                              :fontcolor :white
                                                                              :fillcolor "blue"}
                                                                         :ext {:style :filled
                                                                               :fillcolor "cyan"})))})]
    (clojure.java.io/copy dot (clojure.java.io/file "docs/rems-layers.dot"))
    (clojure.java.io/copy (tangle.core/dot->svg dot) (clojure.java.io/file "docs/rems-layers.svg"))
    (clojure.java.io/copy (tangle.core/dot->image dot "png") (clojure.java.io/file "docs/rems-layers.png"))))

(comment
  (graph))

(deftest test-architecture-layers
  (is true)) ; TODO implement as test
