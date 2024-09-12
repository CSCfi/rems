(ns rems.common.dependency
  (:require [clojure.set]
            [com.stuartsierra.dependency :as dep]))

(defn make-graph
  "Returns a new, empty, dependency graph."
  []
  (dep/graph))

(def ^:private nil-or-empty?
  (some-fn nil?
           (every-pred sequential? empty?)))

(defn depend
  "Returns a new graph with dependency from `node` to each item in `deps`.
   Forbids circular dependencies."
  [g node deps]
  (reduce #(dep/depend %1 node %2)
          g
          (remove nil-or-empty? (flatten [deps]))))

(defn get-dependencies
  "Given dependency graph `g` returns the set of immediate dependencies of `node`."
  [g node]
  (dep/immediate-dependencies g node))

(defn get-transitive-dependencies
  "Given dependency graph `g` returns the set of transitive dependencies (without immediate dependencies) of `node`."
  [g node]
  (clojure.set/difference (dep/transitive-dependencies g node)
                          (dep/immediate-dependencies g node)))

(defn get-all-dependencies
  "Given dependency graph `g` returns a map of all things which `node` depends on, directly or transitively."
  [g node]
  (dep/transitive-dependencies g node))

(defn get-dependents
  "Given dependency graph `g` returns the set of immediate dependents of `node`."
  [g node]
  (dep/immediate-dependents g node))

(defn get-transitive-dependents
  "Given dependency graph `g` returns the set of immediate dependents (without immediate dependents) of `node`."
  [g node]
  (clojure.set/difference (dep/transitive-dependents g node)
                          (dep/immediate-dependents g node)))

(defn get-all-dependents
  "Given dependency graph `g` returns a map of all things which depend upon `node`, directly or transitively."
  [g node]
  (dep/transitive-dependents g node))
