(ns rems.actions.remark
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-button action-form-view comment-field-view
                                             button-wrapper command!]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "remark")

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db
               ::comment ""
               ::public false)
    :dispatch [:rems.actions.components/set-attachments action-form-id []]}))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(rf/reg-sub ::public (fn [db _] (::public db)))
(rf/reg-event-db ::set-public (fn [db [_ value]] (assoc db ::public value)))

(rf/reg-event-fx
 ::send-remark
 (fn [_ [_ {:keys [application-id comment public attachments on-finished]}]]
   (command! :application.command/remark
             {:application-id application-id
              :comment comment
              :public public
              :attachments attachments}
             {:description [text :t.actions/remark]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn remark-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/remark)
                  :on-click #(rf/dispatch [::open-form])}])

(defn remark-view
  [{:keys [application-id
           comment on-set-comment public on-set-public on-send]}]
  [action-form-view action-form-id
   (text :t.actions/remark)
   [[button-wrapper {:id action-form-id
                     :text (text :t.actions/remark)
                     :class "btn-primary"
                     :on-click on-send}]]
   [:div
    [comment-field-view {:id action-form-id
                         :label (text :t.form/add-remark)
                         :comment comment
                         :on-comment on-set-comment}]
    (let [id (str "public-" action-form-id)]
      [:div.form-group
       [:div.form-check
        [:input.form-check-input {:type "checkbox"
                                  :id id
                                  :name id
                                  :value public
                                  :on-change #(on-set-public (.. % -target -checked))}]
        [:label.form-check-label {:for id}
         (text :t.actions/remark-public)]]])
    [action-attachment {:key action-form-id
                        :application-id application-id}]]])

(defn remark-form [application-id on-finished]
  (let [attachments @(rf/subscribe [:rems.actions.components/attachments action-form-id])
        comment @(rf/subscribe [::comment])
        public @(rf/subscribe [::public])]
    [remark-view {:application-id application-id
                  :comment comment
                  :on-set-comment #(rf/dispatch [::set-comment %])
                  :public public
                  :on-set-public #(rf/dispatch [::set-public %])
                  :on-send #(rf/dispatch [::send-remark {:application-id application-id
                                                         :comment comment
                                                         :public public
                                                         :attachments attachments
                                                         :on-finished on-finished}])}]))
