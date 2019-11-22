(ns rems.administration.blacklist
  "Implements both a blacklist component and the blacklist-page"
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.application-util]
            [rems.atoms :as atoms]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text localize-time]]
            [rems.util :refer [fetch post!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:dispatch-n [[::fetch-blacklist {}]
                 [:rems.table/reset]]}))

(rf/reg-event-fx
 ::fetch-blacklist
 (fn [{:keys [db]} [_ params]]
   (let [description [text :t.administration/blacklist]]
     (fetch "/api/blacklist"
            {:url-params params
             :handler #(rf/dispatch [::fetch-result %])
             :error-handler (flash-message/default-error-handler :top description)}))
   {:db (assoc db ::loading? true)}))

(rf/reg-event-fx
 ::add-to-blacklist
 (fn [{:keys [db]} [_ resource user comment]]
   (let [description [text :t.administration/add]]
     (post! "/api/blacklist/add"
            {:params {:blacklist/resource (select-keys resource [:resource/ext-id])
                      :blacklist/user (select-keys user [:userid])
                      :comment comment}
             :handler (flash-message/default-success-handler
                       :top
                       description
                       (fn []
                         (rf/dispatch [::fetch-blacklist])
                         (rf/dispatch [::set-selected-user nil])))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(rf/reg-event-fx
 ::remove-from-blacklist
 (fn [{:keys [db]} [_ resource user comment]]
   (let [description [text :t.administration/remove]]
     (post! "/api/blacklist/remove"
            {:params {:blacklist/resource (select-keys resource [:resource/ext-id])
                      :blacklist/user (select-keys user [:userid])
                      :comment comment}
             :handler (flash-message/default-success-handler
                       :top
                       description
                       #(rf/dispatch [::fetch-blacklist]))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(rf/reg-event-fx
 ::fetch-users
 (fn []
   (fetch "/api/blacklist/users"
          {:handler #(rf/dispatch [::fetch-users-result %])
           :error-handler (flash-message/default-error-handler :top "Fetch users")})
   {}))

(rf/reg-event-db
 ::fetch-users-result
 (fn [db [_ users]]
   (assoc db ::all-users (map atoms/enrich-user users))))

(rf/reg-sub
 ::all-users
 (fn [db _]
   (::all-users db)))

(rf/reg-event-db
 ::set-selected-user
 (fn [db [_ user]]
   (assoc db ::selected-user user)))

(rf/reg-sub
 ::selected-user
 (fn [db _]
   (::selected-user db)))

(defn add-user-form [resource]
  (let [user-field-id "blacklist-user"
        all-users @(rf/subscribe [::all-users])
        selected-users @(rf/subscribe [::selected-user])]
    [:form.form-inline
     {:on-submit (fn [event]
                   (.preventDefault event)
                   ;; TODO: more fancy input form for the comment?
                   (when-some [comment (js/prompt (text :t.administration/comment))]
                     (rf/dispatch [::add-to-blacklist resource selected-users comment])))}

     [:label.my-1.mr-2
      {:for user-field-id}
      (text :t.administration/user)]

     [dropdown/dropdown
      {:id user-field-id
       :class "w-50 my-1 mr-2"
       :items all-users
       :item-key :userid
       :item-label :display
       :item-selected? #(= (:userid selected-users) (:userid %))
       :on-change #(rf/dispatch [::set-selected-user %])}]

     [:button.btn.btn-primary
      {:type :submit}
      (text :t.administration/add)]]))

(defn- remove-button [resource user]
  [:button.btn.btn-secondary.button-min-width
   {:type :button
    :on-click (fn [_event]
                (when-some [comment (js/prompt (text :t.administration/comment))]
                  (rf/dispatch [::remove-from-blacklist resource user comment])))}
   (text :t.administration/remove)])

(defn- format-rows [rows]
  (doall
   (for [{resource :blacklist/resource
          user :blacklist/user
          added-by :blacklist/added-by
          added-at :blacklist/added-at
          comment :blacklist/comment} rows]
     {:key (str "blacklist-" resource (:userid user))
      :resource {:value (:resource/ext-id resource)}
      :user {:value (rems.application-util/get-member-name user)}
      :userid {:value (:userid user)}
      :email {:value (:email user)}
      :added-by {:value (rems.application-util/get-member-name added-by)}
      :added-at {:value added-at :display-value (localize-time added-at)}
      :comment {:value comment}
      :commands {:td [:td.commands
                      [remove-button resource user]]}})))

(rf/reg-event-db
 ::fetch-result
 (fn [db [_ rows]]
   (-> db
       (assoc ::blacklist (format-rows rows))
       (dissoc ::loading?))))

(rf/reg-sub ::blacklist (fn [db _] (::blacklist db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- blacklist-table []
  (let [table-spec {:id ::blacklist
                    :columns [{:key :resource
                               :title (text :t.administration/resource)}
                              {:key :user
                               :title (text :t.administration/user)}
                              {:key :userid
                               :title (text :t.administration/userid)}
                              {:key :email
                               :title (text :t.applicant-info/email)}
                              {:key :added-at
                               :title (text :t.administration/added-at)}
                              {:key :added-by
                               :title (text :t.administration/added-by)}
                              {:key :comment
                               :title (text :t.administration/comment)}
                              {:key :commands
                               :sortable? false
                               :filterable? false}]
                    :rows [::blacklist]
                    :default-sort-column :resource}]
    [:div.mt-3
     [table/search table-spec]
     [table/table table-spec]]))

(defn blacklist []
  (if @(rf/subscribe [::loading?])
    [spinner/big]
    [blacklist-table]))

(defn blacklist-page []
  [:div
   [administration-navigator-container]
   [atoms/document-title (text :t.administration/blacklist)]
   [flash-message/component :top]
   [blacklist]])
