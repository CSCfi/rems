(ns rems.administration.blacklist
  "Implements both a blacklist component and the blacklist-page"
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.common.application-util]
            [rems.atoms :as atoms]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text text-format localize-time]]
            [rems.util :refer [fetch post!]]))

(def +blacklist-add-roles+ #{:owner :handler}) ;; same roles as in rems.api.blacklist

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:dispatch-n [[::fetch-blacklist {}]
                 [:rems.table/reset]
                 [:rems.administration.administration/remember-current-page]]}))

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
                      :comment (or comment "")}
             :handler (flash-message/default-success-handler
                       :top
                       description
                       (fn []
                         (rf/dispatch [::fetch-blacklist])
                         (rf/dispatch [::set-validation-errors nil])
                         (rf/dispatch [::set-selected-user nil])
                         (rf/dispatch [::set-comment nil])))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(rf/reg-event-fx
 ::remove-from-blacklist
 (fn [{:keys [db]} [_ resource user comment]]
   (let [description [text :t.administration/remove]]
     (post! "/api/blacklist/remove"
            {:params {:blacklist/resource (select-keys resource [:resource/ext-id])
                      :blacklist/user (select-keys user [:userid])
                      :comment (or comment "")}
             :handler (flash-message/default-success-handler
                       :top
                       description
                       #(rf/dispatch [::fetch-blacklist]))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(rf/reg-event-fx
 ::fetch-users
 (fn [{:keys [db]} _]
   (when (some +blacklist-add-roles+ (get-in db [:identity :roles]))
     (fetch "/api/blacklist/users"
            {:handler #(rf/dispatch [::fetch-users-result %])
             :error-handler (flash-message/default-error-handler :top "Fetch users")}))
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

(rf/reg-event-db
 ::set-comment
 (fn [db [_ user]]
   (assoc db ::comment user)))

(rf/reg-sub
 ::comment
 (fn [db _]
   (::comment db)))

(rf/reg-event-db
 ::set-validation-errors
 (fn [db [_ user]]
   (assoc db ::validation-errors user)))

(rf/reg-sub
 ::validation-errors
 (fn [db _]
   (::validation-errors db)))

(defn user-field [id]
  (let [all-users @(rf/subscribe [::all-users])
        selected-users @(rf/subscribe [::selected-user])
        error (:user @(rf/subscribe [::validation-errors]))
        error-id (str id "-error")]
    [:<>
     ;; TODO: add aria-describedby pointing to error-id
     ;; TODO: highlight an invalid dropdown similar to "form-control is-invalid" for normal input fields
     [dropdown/dropdown
      {:id id
       :items all-users
       :item-key :userid
       :item-label :display
       :item-selected? #(= (:userid selected-users) (:userid %))
       :on-change #(rf/dispatch [::set-selected-user %])}]

     (when error
       [:div.invalid-feedback
        {:id error-id
         :style {:display :block}} ; XXX: .invalid-feedback is hidden unless it's a sibling of .form-control.is-invalid
        error])]))

(defn add-user-form-impl [resource]
  (let [user-field-id "blacklist-user"
        comment-field-id "blacklist-comment"
        selected-user @(rf/subscribe [::selected-user])
        comment @(rf/subscribe [::comment])]
    [:form
     {:on-submit (fn [event]
                   (.preventDefault event)
                   (if-some [errors (cond-> nil
                                      (nil? selected-user) (assoc :user [#(text-format :t.form.validation/required
                                                                                       (text :t.administration/user))]))]
                     (do
                       (rf/dispatch [::set-validation-errors errors])
                       (.focus (js/document.getElementById user-field-id)))
                     (rf/dispatch [::add-to-blacklist resource selected-user comment])))}

     [:div.form-group.row
      [:label.col-sm-1.col-form-label
       {:for user-field-id}
       (text :t.administration/user)]

      [:div.col-sm-6
       [user-field user-field-id]]]

     [:div.form-group.row
      [:label.col-sm-1.col-form-label
       {:for comment-field-id}
       (text :t.administration/comment)]

      [:div.col-sm-6
       [:input.form-control
        {:id comment-field-id
         :type :text
         :value comment
         :on-change #(rf/dispatch [::set-comment (-> % .-target .-value)])}]]]

     [:div.form-group.row
      [:div.col-sm-1]
      [:div.col-sm-6
       [:button#blacklist-add.btn.btn-primary
        {:type :submit}
        (text :t.administration/add)]]]]))

(defn add-user-form [resource]
  [roles/show-when +blacklist-add-roles+ [add-user-form-impl resource]])

(defn- remove-button [resource user]
  [:button.btn.btn-secondary.button-min-width
   {:type :button
    :on-click (fn [_event]
                ;; TODO add form & field for comment
                (rf/dispatch [::remove-from-blacklist resource user ""]))}
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
      :user {:value (rems.common.application-util/get-member-name user)}
      :userid {:value (:userid user)}
      :email {:value (:email user)}
      :added-by {:value (rems.common.application-util/get-member-name added-by)}
      :added-at {:value added-at :display-value (localize-time added-at)}
      :comment {:value comment}
      :commands {:display-value [:div.commands [remove-button resource user]]}})))

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
                               :filterable? false
                               :aria-label (text :t.actions/commands)}]
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
   [administration/navigator]
   [atoms/document-title (text :t.administration/blacklist)]
   [flash-message/component :top]
   [blacklist]])
