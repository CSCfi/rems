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
            [rems.trace]
            [rems.db.test-data-helpers :as test-helpers]))


(defn interesting-ns? [s]
  (and (str/starts-with? s "rems.")
       ;; utils and helpers are not interesting
       (not (str/ends-with? s ".util"))
       (not (str/ends-with? s "-util"))
       (not (str/ends-with? s "-helpers"))
       (not (str/starts-with? s "rems.api.schema"))

       ;; special cases
       (not (str/starts-with? s "rems.service.test-data"))
       (not (str/starts-with? s "rems.service.dependencies"))

       ;; not interesting right now
       (not (str/starts-with? s "rems.main"))

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
    [:service :service] false ; circular dependency
    [:api :ext] true
    [:service :ext] true
    [:ext :ext] true
    [:db :core] (= "rems.db.core" (:name to))
    [:api :api] (= "rems.api" (:name from))
    false))
(defn analyze-code []
  (let [ret (sh/sh "clj-kondo" "--lint" "src" "--parallel" "--config" "{:output {:format :edn} :analysis true}")
        analysis (-> ret :out edn/read-string :analysis)
        namespaces (->> analysis
                        :namespace-definitions
                        (mapv (comp str :name))
                        ;;(concat ["rems.cli"]) ; consider implementing `rems.cli` ns
                        (mapv rename-ns)
                        (filterv interesting-ns?)
                        (mapv #(merge {:name %}
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
                              (mapv (juxt (comp str :from) (comp str :to)))
                              distinct
                              (mapv (fn [[from to]] [(rename-ns from) (rename-ns to)]))
                              (filterv (fn [[from to]]
                                         (and (interesting-ns? from)
                                              (interesting-ns? to))))
                              ;;(concat [["rems.cli"]])  ; consider implementing `rems.cli` ns
                              ;;(remove #(ok-transition? (namespace-by-id (first %)) (namespace-by-id (second %))))
                              (mapv (fn [[from to]] [from to (if (ok-transition? (namespace-by-id from)
                                                                                 (namespace-by-id to))
                                                               {:color :black
                                                                :constraint true
                                                                :weight 1}
                                                               {:color :red
                                                                :constraint false
                                                                :weight 0.01})])))
        nodes-with-edges (doall (into #{} (mapcat (partial take 2) namespace-usages)))
        namespaces (filterv (comp nodes-with-edges :name) namespaces)]
    (prn nodes-with-edges namespaces)
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

(defn- sum [coll]
  (reduce + 0.0 coll))

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
                   (mapv (juxt :from :to))
                   (remove #(or (nil? (first %)) (nil? (second %))))
                   vec)
        edge-freqs (frequencies edges)
        calls-by-ids (group-by (juxt :from :to)
                               (for [call trace] call))
        hide-cutoff-ms 0
        cutoff-ms 0 ;;0.5
        edges (doall (for [[from to] (distinct edges)
                           :let [calls (calls-by-ids [from to])]
                           :when (seq calls)
                           :let [c (edge-freqs [from to])
                                 times (->> calls
                                            (mapv :elapsed-time)
                                            (remove nil?))
                                 _ (prn from to times)
                                 avg (/ (average times) 1000.0)
                                 sum-time (/ (sum times) 1000.0)
                                 min-time (if (seq times) (/ (apply min times) 1000.0) 0)
                                 max-time (if (seq times) (/ (apply max times) 1000.0) 0)
                                 lazy (count (filter :lazy? calls))]
                           :when (> max-time hide-cutoff-ms)]
                       [(sym->id from) (sym->id to) {;;:color (if (and avg (< avg cutoff-ms)) "#gray" "black")
                                                     :weight (or (and min-time (number? min-time)) 0.01)
                                                     :penwidth (max (or (and min-time (number? min-time) (Math/sqrt min-time)) 0.1)
                                                                    0.1)
                                                     :label (if (and max-time (< max-time cutoff-ms))
                                                              ""
                                                              [:table {:border 0}
                                                               [:tr [:td "count"] [:td c]]
                                                               (when (> c 1) [:tr [:td "sum"] [:td (format "%.2f ms" sum-time)]])
                                                               (when (> c 1) [:tr [:td "min"] [:td (format "%.2f ms" min-time)]])
                                                               [:tr [:td (if (> c 1) "avg" "")] [:td (format "%.2f ms" avg)]]
                                                               (when (> c 1) [:tr [:td "max"] [:td (format "%.2f ms" max-time)]])
                                                               (cond (= lazy c) [:tr [:td "lazy"] [:td "true"]]
                                                                     (pos? lazy) [:tr [:td "lazy"] [:td (format "%d/%d (%.0f%%)" (int lazy) (int c) (* 100.0 (/ lazy (double c))))]])])}]))
        node-has-edge? (into #{} (concat (mapv first edges) (mapv second edges)))
        nodes (filterv (comp node-has-edge? :id) nodes)
        dot (tangle.core/graph->dot nodes
                                    edges
                                    {:directed? true
                                     :graph {:rankdir :LR
                                             ;;:rankdir :TB
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
    (clojure.java.io/copy (tangle.core/dot->image dot "png") (clojure.java.io/file "docs/rems-call-graph.png"))
    (clojure.java.io/copy (tangle.core/dot->image dot "jpg") (clojure.java.io/file "docs/rems-call-graph.jpg"))))

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
    (rems.service.catalogue/get-localized-catalogue-items {:archived false :enabled true})
    (rems.service.catalogue/get-catalogue-tree {:archived false :enabled true})
    (count (rems.service.application/get-applications-with-user "handler"))

    (mount.core/start #'rems.config/env #'rems.db.core/*db* #'rems.locales/translations #'rems.application.search/search-index)
    (rems.trace/unbind-rems)
    (rems.trace/bind-rems)
    (rems.service.application/get-full-public-application 3018)
    (rems.db.applications/get-application-internal 3019)
    (test-helpers/create-draft! "elsa" [1] "trace test")
    (repeatedly 100 (fn []
                      (rems.service.command/command! {:type :application.command/create
                                                      :actor "elsa"
                                                      :time (clj-time.core/date-time 2001)
                                                      :catalogue-item-ids [1]})
                      (rems.service.command/command! {:type :application.command/save-draft
                                                      :actor "elsa"
                                                      :time (clj-time.core/date-time 2001)
                                                      :application-id 3019
                                                      :field-values []})))
    (call-graph @rems.trace/trace-a)))
