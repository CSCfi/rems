(ns rems.trace
  (:require [medley.core :refer [remove-vals]]
            [rems.service.catalogue]
            [rems.db.catalogue]))

(def trace-a (atom []))
(def stack-a (atom []))

(defn reset! []
  (clojure.core/reset! trace-a [])
  (clojure.core/reset! stack-a []))

(defn tracer [f sym & args]
  (try (swap! stack-a conj sym)
       (let [start-time (System/nanoTime)
             result (apply f args)
             elapsed-time (- (System/nanoTime) start-time)]
         (swap! stack-a pop)
         (swap! trace-a conj
                (remove-vals nil? {:from (last @stack-a)
                                   :to sym
                                   :elapsed-time (double (/ elapsed-time 1000.0))
                                   :lazy? (and (instance? clojure.lang.IPending result)
                                               (not (realized? result)))}))
         result)
       (catch Throwable t
         (.printStackTrace t)
         (swap! stack-a pop)
         (swap! trace-a conj
                (remove-vals nil? {:from (last @stack-a)
                                   :to sym
                                   :error (.getMessage t)})))))

(defn wrap-trace [f sym]
  (partial #'tracer f sym))

(defn bind [^clojure.lang.Var v ^clojure.lang.Symbol sym ^clojure.lang.IFn f]
  (let [m (meta v)
        original-f (::trace m f)]
    (alter-var-root v wrap-trace sym)
    (alter-meta! v assoc ::trace original-f)))

(defn unbind [^clojure.lang.Var v]
  (let [m (meta v)
        original-f (::trace m)]
    (alter-var-root v identity)
    (alter-meta! v dissoc ::trace original-f)))

(defn examine-ns [ns-sym]
  (let [n (the-ns ns-sym)]
    (for [[sym v] (ns-interns n)
          :let [f-sym (symbol (name ns-sym) (name sym))
                f (ns-resolve n sym)
                f (if (var? f) (deref f) f)]]
      {:var v
       :f-sym f-sym
       :f f
       :fn? (fn? f)
       :type (type f)})))

(defn bind-ns [ns-sym]
  (doseq [x (examine-ns ns-sym)
          :when (:fn? x)]
    (bind (:var x) (:f-sym x) (:f x))))

(defn unbind-ns [ns-sym]
  (doseq [x (examine-ns ns-sym)
          :when (::trace (meta (:var x)))]
    (unbind (:var x))))

(defn bind-rems []
  (doseq [ns-sym (->> (all-ns)
                      (map ns-name)
                      (filterv #(clojure.string/starts-with? % "rems."))
                      (remove #{'rems.trace 'rems.db.outbox}))]
    (prn :instrumenting ns-sym)
    (bind-ns ns-sym)))

(defn unbind-rems []
  (doseq [ns-sym (->> (all-ns)
                      (map ns-name)
                      (filterv #(clojure.string/starts-with? % "rems."))
                      (remove #{'rems.trace 'rems.db.outbox}))]
    (prn :uninstrumenting ns-sym)
    (unbind-ns ns-sym)))

(comment
  (do (rems.service.catalogue/get-catalogue-items nil)
      {:trace @trace-a
       :stack @stack-a})

  (ns-publics 'rems.db.core)

  (ns-resolve (the-ns 'rems.service.catalogue) 'get-localized-catalogue-items)

  (ns-publics (the-ns 'rems.service.catalogue))

  (bind #'rems.service.catalogue/get-catalogue-items
        'rems.service.catalogue/get-catalogue-items
        rems.service.catalogue/get-catalogue-items)

  (bind #'rems.db.catalogue/get-catalogue-items
        'rems.db.catalogue/get-catalogue-items
        rems.db.catalogue/get-catalogue-items)

  (bind-ns 'rems.service.catalogue)
  (bind-ns 'rems.db.catalogue)
  (examine-ns 'rems.db.core)
  (bind-ns 'rems.db.core)
  (unbind-ns 'rems.db.core)

  (meta #'rems.service.catalogue/change-form!)

  (ns-name (first (all-ns)))

  (def instrumented-namespaces (atom (->> (all-ns)
                                          (map ns-name)
                                          (filterv #(clojure.string/starts-with? % "rems."))
                                          (remove #{'rems.trace 'rems.db.outbox}))))

  (doseq [ns-sym @instrumented-namespaces]
    (prn :instrumenting ns-sym)
    (bind-ns ns-sym))

  (bind-ns 'rems.locales)

  (count (rems.service.catalogue/get-catalogue-items nil)))
