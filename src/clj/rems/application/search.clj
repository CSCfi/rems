(ns rems.application.search
  (:require [rems.db.applications :as applications]))

(defn find-applications [query]
  (let [apps (applications/get-all-unrestricted-applications)]
    ;; TODO: index apps
    ;; TODO: query the index
    (->> apps
         ;; TODO: remove walking skeleton
         (filter #(= query (name (:application/applicant %))))
         (map :application/id)
         (set))))
