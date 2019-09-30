(ns rems.actions.add-licenses
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper collapse-action-form]]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text get-localized-title]]
            [rems.util :refer [fetch post!]]))

(rf/reg-fx
 ::fetch-licenses
 (fn [on-success]
   (fetch "/api/licenses"
          {:handler on-success
           :error-handler (flash-message/default-error-handler :top "Fetch licenses")})))

(rf/reg-event-fx
 ::open-form
 (fn
   [{:keys [db]} _]
   {:db (assoc db
               ::comment ""
               ::potential-licenses #{}
               ::selected-licenses #{})
    ::fetch-licenses #(rf/dispatch [::set-potential-licenses %])}))

(defn- assoc-all-titles
  "Prepopulate `:all-titles` property to facilitate searching with localized names and unlocalized title"
  [license]
  (assoc license :all-titles (str/join "" (conj (mapcat :title (vals (:localizations license)))
                                                (:title license)))))

(rf/reg-sub ::potential-licenses (fn [db _] (::potential-licenses db)))
(rf/reg-event-db
 ::set-potential-licenses
 (fn [db [_ licenses]]
   (assoc db
          ::potential-licenses (set (map assoc-all-titles licenses))
          ::selected-licenses #{})))

(rf/reg-sub ::selected-licenses (fn [db _] (::selected-licenses db)))
(rf/reg-event-db ::set-selected-licenses (fn [db [_ licenses]] (assoc db ::selected-licenses (sort-by :id licenses))))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(def ^:private action-form-id "add-licenses")
(def ^:private dropdown-id "add-licenses-dropdown")

(rf/reg-event-fx
 ::send-add-licenses
 (fn [_ [_ {:keys [application-id licenses comment on-finished]}]]
   (let [description [text :t.actions/add-licenses]]
     (post! "/api/applications/add-licenses"
            {:params {:application-id application-id
                      :comment comment
                      :licenses (map :id licenses)}
             :handler (flash-message/default-success-handler
                       :actions
                       description
                       (fn [_]
                         (collapse-action-form action-form-id)
                         (on-finished)))
             :error-handler (flash-message/default-error-handler :actions description)}))
   {}))

(defn add-licenses-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/add-licenses)
                  :on-click #(rf/dispatch [::open-form])}])

(defn add-licenses-view
  [{:keys [selected-licenses potential-licenses comment language on-set-comment on-set-licenses on-send]}]
  [action-form-view action-form-id
   (text :t.actions/add-licenses)
   [[button-wrapper {:id "add-licenses"
                     :text (text :t.actions/add-licenses)
                     :class "btn-primary"
                     :on-click on-send}]]
   [:div
    [action-comment {:id action-form-id
                     :label (text :t.form/add-comments-shown-to-applicant)
                     :comment comment
                     :on-comment on-set-comment}]
    [:div.form-group
     [:label {:for dropdown-id} (text :t.actions/licenses-selection)]
     [dropdown/dropdown
      {:id dropdown-id
       :items potential-licenses
       :item-key :id
       :item-label #(get-localized-title % language)
       :item-selected? #(contains? (set selected-licenses) %)
       :multi? true
       :on-change on-set-licenses}]]]])

(defn add-licenses-form [application-id on-finished]
  (let [selected-licenses @(rf/subscribe [::selected-licenses])
        potential-licenses @(rf/subscribe [::potential-licenses])
        comment @(rf/subscribe [::comment])
        language @(rf/subscribe [:language])]
    [add-licenses-view {:selected-licenses selected-licenses
                        :potential-licenses potential-licenses
                        :comment comment
                        :language language
                        :on-set-comment #(rf/dispatch [::set-comment %])
                        :on-set-licenses #(rf/dispatch [::set-selected-licenses %])
                        :on-send #(rf/dispatch [::send-add-licenses {:application-id application-id
                                                                     :licenses selected-licenses
                                                                     :comment comment
                                                                     :on-finished on-finished}])}]))
