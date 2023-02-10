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
            [tangle.core]
            [rems.trace]))


(defn interesting-ns? [s]
  (and (str/starts-with? s "rems.")
       (not (str/ends-with? s ".util"))
       (not (str/ends-with? s "-util"))
       (not (str/starts-with? s "rems.api.schema"))
       (not (str/starts-with? s "rems.service.dependencies"))
       (or (= s "rems.main")
           (= s "rems.cli")
           (str/starts-with? s "rems.ext.")
           ;;(str/starts-with? s "rems.cli")
           (str/starts-with? s "rems.db.")
           (str/starts-with? s "rems.service.")
           (str/starts-with? s "rems.api"))))

;; use to see what for future namespace renaming could look like
(defn rename-ns [s]
  (-> s
      (str/replace "rems.db.fix-userid" "rems.service.fix-userid")))

(defn ok-transition? [from to]
  (case [(:layer from) (:layer to)]
    [:main :service] true
    [:cli :service] true
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
                        (map (comp str :name))
                        ;;(concat ["rems.cli"]) ; consider implementing `rems.cli` ns
                        (map rename-ns)
                        (filter interesting-ns?)
                        (map #(merge {:name %}
                                     (cond (str/starts-with? % "rems.ext.") {:layer :ext}
                                           (str/starts-with? % "rems.db.core") {:layer :core}
                                           (str/starts-with? % "rems.db.") {:layer :db}
                                           (str/starts-with? % "rems.service.") {:layer :service}
                                           (str/starts-with? % "rems.api") {:layer :api}
                                           (str/starts-with? % "rems.main") {:layer :main}
                                           (str/starts-with? % "rems.cli") {:layer :cli}))))

        namespace-by-id (index-by :name namespaces)

        namespace-usages (->> analysis
                              :namespace-usages
                              (map (juxt (comp str :from) (comp str :to)))
                              distinct
                              (map (fn [[from to]] [(rename-ns from) (rename-ns to)]))
                              (filter (fn [[from to]]
                                        (and (interesting-ns? from)
                                             (interesting-ns? to))))
                              ;;(concat [["rems.cli"]])  ; consider implementing `rems.cli` ns
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
                                                                         (:cli :main)
                                                                         {:style :filled
                                                                          :fontcolor :white
                                                                          :fillcolor "magenta"}

                                                                         :api
                                                                         {:style :filled
                                                                          :fontcolor :white
                                                                          :fillcolor "red"}

                                                                         :service
                                                                         {:style :filled
                                                                          :fillcolor "#00ff00"}

                                                                         :db
                                                                         {:style :filled
                                                                          :fontcolor :white
                                                                          :fillcolor "blue"}

                                                                         :core
                                                                         {:style :filled
                                                                          :fillcolor "cyan"}

                                                                         :ext
                                                                         {:style :filled
                                                                          :fillcolor "cyan"})))})]
    (clojure.java.io/copy dot (clojure.java.io/file "docs/rems-layers.dot"))
    (clojure.java.io/copy (tangle.core/dot->svg dot) (clojure.java.io/file "docs/rems-layers.svg"))
    (clojure.java.io/copy (tangle.core/dot->image dot "png") (clojure.java.io/file "docs/rems-layers.png"))))

(comment
  (graph))

(deftest test-architecture-layers
  (is true)) ; TODO implement as test

(defn- sym->id [sym]
  (pr-str sym))

(defn escape-html [s]
  (-> s
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- average [coll]
  (let [coll (remove nil? coll)]
    (if (seq coll)
      (/ (reduce + coll) (count coll))
      (double 0))))

(defn call-graph [trace]
  (let [nodes (->> trace
                   (mapcat (juxt :from :to))
                   (remove nil?)
                   distinct
                   (mapv #(merge {:id (sym->id %)
                                  :ns (name (namespace %))
                                  :name (name %)}
                                 (cond (str/starts-with? % "rems.ext.") {:layer :ext}
                                       (str/starts-with? % "rems.db.core") {:layer :core}
                                       (str/starts-with? % "rems.db.") {:layer :db}
                                       (str/starts-with? % "rems.service.") {:layer :service}
                                       (str/starts-with? % "rems.api") {:layer :api}
                                       (str/starts-with? % "rems.main") {:layer :main}
                                       (str/starts-with? % "rems.cli") {:layer :cli}))))
        edges (->> trace
                   (map (juxt :from :to))
                   (remove #(or (nil? (first %)) (nil? (second %)))))
        edge-freqs (frequencies edges)
        calls-by-ids (group-by (juxt :from :to)
                               (for [call trace] call))
        edges (for [[from to] (distinct edges)
                    :let [calls (calls-by-ids [from to])
                          c (edge-freqs [from to])
                          _ (prn (mapv :elapsed-time calls))
                          avg (/ (average (mapv :elapsed-time calls)) 1000.0)
                          lazy (count (filter :lazy? calls))]]
                [(sym->id from) (sym->id to) {:label [:table {:border 0}
                                                      [:tr [:td "count"] [:td c]]
                                                      [:tr [:td "avg"] [:td (format "%.2f ms" avg)]]
                                                      (cond (= lazy c) [:tr [:td "lazy"] [:td "true"]]
                                                            (pos? lazy) [:tr [:td "lazy"] [:td (format "%d/%d (%.0f%%)" (int lazy) (int c) (* 100.0 (/ lazy (double c))))]])]}])
        dot (tangle.core/graph->dot nodes
                                    edges
                                    {:directed? true
                                     :graph {;;:rankdir :LR
                                             :rankdir :TB
                                             ;;:ranksep 1.5
                                             ;;:rank :min
                                             :dpi 150
                                             :layout :dot}
                                     :edge {:penwidth 2}
                                     :node {:shape :box}
                                     :node->id :id
                                     :node->descriptor (fn [node]
                                                         (merge {:label [:table {:border 0}
                                                                         [:tr [:td [:font {:point-size 10} (:ns node)]]]
                                                                         [:tr [:td (escape-html (:name node))]]]}
                                                                (when-let [layer (:layer node)]
                                                                  (case layer
                                                                    (:cli :main)
                                                                    {:style :filled
                                                                     :fontcolor :white
                                                                     :fillcolor "magenta"}

                                                                    :api
                                                                    {:style :filled
                                                                     :fontcolor :white
                                                                     :fillcolor "red"}

                                                                    :service
                                                                    {:style :filled
                                                                     :fillcolor "#00ff00"}

                                                                    :db
                                                                    {:style :filled
                                                                     :fontcolor :white
                                                                     :fillcolor "blue"}

                                                                    :core
                                                                    {:style :filled
                                                                     :fillcolor "cyan"}

                                                                    :ext
                                                                    {:style :filled
                                                                     :fillcolor "cyan"}))))})]
    (clojure.java.io/copy dot (clojure.java.io/file "docs/rems-call-graph.dot"))
    (clojure.java.io/copy (tangle.core/dot->svg dot) (clojure.java.io/file "docs/rems-call-graph.svg"))
    (clojure.java.io/copy (tangle.core/dot->image dot "png") (clojure.java.io/file "docs/rems-call-graph.png"))))

(comment
  (rems.trace/bind-ns 'rems.service.catalogue)
  (rems.trace/bind-ns 'rems.db.catalogue)
  (rems.trace/bind-ns 'rems.db.organizations)
  (rems.trace/bind-ns 'rems.db.users)
  (rems.trace/bind-ns 'rems.db.core)
  (rems.trace/examine-ns 'rems.service.catalogue)

  (rems.trace/bind-rems)

  (do
    (rems.trace/reset!)
    #_(rems.service.catalogue/get-localized-catalogue-items {:archived false :enabled true})
    #_(rems.service.catalogue/get-catalogue-tree {:archived false :enabled true})
    (count (rems.db.applications/get-all-applications "handler"))
    #_(rems.db.applications/get-application 1016)
    (call-graph @rems.trace/trace-a)))
