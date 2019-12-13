(ns rems.actions.assign-external-id
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view button-wrapper collapse-action-form]]
            [rems.atoms :refer [textarea]]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db ::external-id "")}))

(rf/reg-sub ::external-id (fn [db _] (::external-id db)))
(rf/reg-event-db ::set-external-id (fn [db [_ value]] (assoc db ::external-id value)))

(def ^:private action-form-id "assign-external-id")

(rf/reg-event-fx
 ::send
 (fn [_ [_ {:keys [application-id external-id on-finished]}]]
   (let [description [text :t.actions/assign-external-id]]
     (post! "/api/applications/assign-external-id"
            {:params {:application-id application-id
                      :external-id (str/trim external-id)}
             :handler (flash-message/default-success-handler
                       :actions
                       description
                       (fn [_]
                         (collapse-action-form action-form-id)
                         (on-finished)))
             :error-handler (flash-message/default-error-handler :actions description)}))
   {}))

(defn assign-external-id-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/assign-external-id)
                  :on-click #(rf/dispatch [::open-form])}])

(defn assign-external-id-view
  [{:keys [external-id on-set-external-id on-send]}]
  [action-form-view action-form-id
   (text :t.actions/assign-external-id)
   [[button-wrapper {:id "assign-external-id-button"
                     :text (text :t.actions/assign-external-id)
                     :class "btn-primary"
                     :on-click on-send}]]
   (let [id (str action-form-id "-field")]
     [:div.form-group
      [:label {:for id}
       (text :t.actions/assign-external-id-info)]
      [:input.form-control {:type :text
                            :id id
                            :name id
                            :value external-id
                            :on-change #(on-set-external-id (.. % -target -value))}]])])

(defn assign-external-id-form [application-id on-finished]
  (let [external-id @(rf/subscribe [::external-id])]
    [assign-external-id-view {:external-id external-id
                              :on-set-external-id #(rf/dispatch [::set-external-id %])
                              :on-send #(rf/dispatch [::send {:application-id application-id
                                                              :external-id external-id
                                                              :on-finished on-finished}])}]))
