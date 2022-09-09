(ns rems.migrations.refactor-workflow-licenses
  (:require [clojure.test :refer [deftest is]]
            [hugsql.core :as hugsql]
            [rems.json :as json]
            [rems.common.util :refer [build-index]]))

(hugsql/def-db-fns-from-string
  "
-- :name get-workflow-licenses :*
SELECT wfid, licid FROM workflow_licenses;
   
-- :name get-workflow :? :1
SELECT id, workflowbody::TEXT
FROM workflow
WHERE id = :id;
   
-- :name set-workflow-body! :!
UPDATE workflow
SET workflowBody = :workflowbody::jsonb
WHERE id = :id;
")

(defn migrate-workflow-licenses [workflow licenses]
  (assoc-in workflow [:workflowbody :licenses] (sort licenses)))

(defn migrate-workflows! [conn workflow-licenses]
  (let [workflows (->> workflow-licenses
                       (build-index {:keys [:wfid]
                                     :value-fn :licid
                                     :collect-fn set}))]
    (doseq [[wfid licenses] workflows
            :let [workflow (get-workflow conn {:id wfid})
                  _ (assert workflow)
                  workflow (update workflow :workflowbody json/parse-string)]]
      (set-workflow-body! conn (update (migrate-workflow-licenses workflow licenses)
                                       :workflowbody
                                       json/generate-string)))))

(deftest test-migrate-workflows!
  (let [workflow-licenses [{:wfid 1 :licid 10}
                           {:wfid 1 :licid 11}
                           {:wfid 2 :licid 12}]
        workflows (atom [])]
    (with-redefs [get-workflow (fn [_ {:keys [id]}]
                                 {:id id
                                  :workflowbody (json/generate-string {})})
                  set-workflow-body! (fn [_ wf] (swap! workflows conj wf))]
      (migrate-workflows! nil workflow-licenses)
      (is (= [{:id 1 :workflowbody {:licenses [10 11]}}
              {:id 2 :workflowbody {:licenses [12]}}]
             (->> @workflows
                  (mapv #(update % :workflowbody json/parse-string))))))))

(defn migrate-up [{:keys [conn]}]
  (migrate-workflows! conn (get-workflow-licenses conn)))
