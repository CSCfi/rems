(ns rems.service.organizations
  (:require [clojure.set :as set]
            [medley.core :refer [assoc-some find-first]]
            [rems.auth.util]
            [rems.common.util :refer [apply-filters]]
            [rems.db.applications :as applications]
            [rems.db.organizations :as organizations]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.service.dependencies :as dependencies]
            [rems.service.util]))

(defn- can-see-all [userid]
  (let [all-roles (set/union (roles/get-roles userid)
                             (organizations/get-all-organization-roles userid)
                             (applications/get-all-application-roles userid))]
    (some #{:owner :organization-owner :handler :reporter}
          all-roles)))

(defn get-organizations [& [{:keys [userid owner enabled archived]}]]
  (let [check-org-owner? (and (some? owner)
                              (not (contains? (rems.db.roles/get-roles owner) :owner))) ; implicitly owns all
        apply-permissions? (and (some? userid)
                                (not (can-see-all userid)))
        query-filters (assoc-some nil
                                  :enabled enabled
                                  :archived archived)]

    (cond->> (rems.db.organizations/get-organizations)
      query-filters (apply-filters query-filters)
      apply-permissions? (map (fn organization-overview [org]
                                (select-keys org [:organization/id
                                                  :organization/name
                                                  :organization/short-name])))
      check-org-owner? (filter (fn is-organization-owner [org]
                                 (contains? (set (map :userid (:organization/owners org))) owner)))
      true (into []))))

(defn get-organization [userid org]
  (->> (get-organizations {:userid userid})
       (find-first (comp #{(:organization/id org)} :organization/id))))

(defn add-organization! [cmd]
  (if-let [id (organizations/add-organization! cmd)]
    {:success true
     :organization/id id}
    {:success false
     :errors [{:type :t.actions.errors/duplicate-id
               :organization/id (:organization/id cmd)}]}))

(defn edit-organization! [cmd]
  (let [id (:organization/id cmd)]
    (rems.service.util/check-allowed-organization! cmd)
    (organizations/update-organization! id (fn [organization] (->> (dissoc cmd :organization/id)
                                                                   (merge organization))))
    {:success true
     :organization/id id}))

(defn set-organization-enabled! [{:keys [enabled] :as cmd}]
  (let [id (:organization/id cmd)]
    (organizations/update-organization! id (fn [organization] (assoc organization :enabled enabled)))
    {:success true}))

(defn set-organization-archived! [{:keys [archived] :as cmd}]
  (let [id (:organization/id cmd)]
    (or (dependencies/change-archive-status-error archived  {:organization/id id})
        (do
          (organizations/update-organization! id (fn [organization] (assoc organization :archived archived)))
          {:success true}))))

(defn get-available-owners [] (users/get-users))

(defn get-handled-organizations [{:keys [userid]}]
  (for [workflow (workflow/get-workflows)
        :let [handlers (set (mapv :userid (get-in workflow [:workflow :handlers])))]
        :when (contains? handlers userid)
        :let [organization (organizations/getx-organization-by-id (get-in workflow [:organization :organization/id]))]]
    organization))
