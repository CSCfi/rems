(ns rems.actions.remark
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-attachment action-button action-form-view action-comment
                                         button-wrapper command!]]
            [rems.fields :as fields]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db
               ::comment ""
               ::attachment-id nil
               ::public false)}))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(rf/reg-sub ::public (fn [db _] (::public db)))
(rf/reg-event-db ::set-public (fn [db [_ value]] (assoc db ::public value)))

(rf/reg-sub ::attachment-id (fn [db _] (::attachment-id db)))
(rf/reg-event-db ::set-attachment-id (fn [db [_ value]] (assoc db ::attachment-id value)))

(rf/reg-event-fx
 ::save-attachment
 (fn [{:keys [db]} [_ application-id file]]
   (let [description [text :t.form/upload]]
     (post! "/api/applications/add-attachment"
            {:url-params {:application-id application-id}
             :body file
             :handler (flash-message/default-success-handler
                       :actions
                       description
                       (fn [response]
                         (rf/dispatch [::set-attachment-id (:id response)])))
             :error-handler (flash-message/default-error-handler :actions description)})
     {})))


(def ^:private action-form-id "remark")

(rf/reg-event-fx
 ::send-remark
 (fn [{:keys [db]} [_ {:keys [application-id on-finished]}]]
   (command! :application.command/remark
             {:application-id application-id
              :comment (::comment db)
              :public (::public db)
              :attachments (if-let [id (::attachment-id db)]
                             [{:attachment/id id}]
                             [])}
             {:description [text :t.actions/remark]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn remark-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/remark)
                  :on-click #(rf/dispatch [::open-form])}])

(defn remark-view
  [{:keys [comment on-set-comment public on-set-public on-send
           attachment on-attach on-remove-attachment]}]
  [action-form-view action-form-id
   (text :t.actions/remark)
   [[button-wrapper {:id action-form-id
                     :text (text :t.actions/remark)
                     :class "btn-primary"
                     :on-click on-send}]]
   [:div
    [action-comment {:id action-form-id
                     :label (text :t.form/add-remark)
                     :comment comment
                     :on-comment on-set-comment}]
    (let [id (str "public-" action-form-id)]
      [:div.form-check
       [:input.form-check-input {:type "checkbox"
                                 :id id
                                 :name id
                                 :value public
                                 :on-change #(on-set-public (.. % -target -checked))}]
       [:label.form-check-label {:for id}
        (text :t.actions/remark-public)]])
    [action-attachment {:id action-form-id
                        :attachment attachment
                        :on-attach on-attach
                        :on-remove-attachment on-remove-attachment}]]])

(defn remark-form [application-id on-finished]
  [remark-view {:comment @(rf/subscribe [::comment])
                :on-set-comment #(rf/dispatch [::set-comment %])
                :public @(rf/subscribe [::public])
                :on-set-public #(rf/dispatch [::set-public %])
                :on-send #(rf/dispatch [::send-remark {:application-id application-id
                                                       :on-finished on-finished}])
                :attachment @(rf/subscribe [::attachment-id])
                :on-attach #(rf/dispatch [::save-attachment application-id %])
                :on-remove-attachment #(rf/dispatch [::set-attachment-id nil])}])
