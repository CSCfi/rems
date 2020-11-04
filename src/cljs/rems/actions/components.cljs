(ns rems.actions.components
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [enrich-user textarea]]
            [rems.common.attachment-types :as attachment-types]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.fields :as fields]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [post!]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn- action-collapse-id [action-id]
  (str "actions-" action-id))

(defn- action-button-id [action-id]
  (str action-id "-action-button"))

(defn button-wrapper [{:keys [text class] :as props}]
  [:button.btn
   (merge {:type :button
           :class (or class :btn-secondary)}
          (dissoc props :class :text))
   text])

(defn collapse-action-form [id]
  (.collapse (js/$ (str "#" (action-collapse-id id))) "hide"))

(defn cancel-action-button [id]
  [:button.btn.btn-secondary
   {:type :button
    :id (str "cancel-" id)
    :data-toggle "collapse"
    :data-target (str "#" (action-collapse-id id))
    :on-click #(.focus (.querySelector js/document (str "#" (action-button-id id))))}
   (text :t.actions/cancel)])

(defn comment-field-view [{:keys [id label comment on-comment]}]
  (let [id (str "comment-" id)]
    [:div.form-group
     [:label {:for id} label]
     [textarea {:id id
                :min-rows 4
                :max-rows 4
                :name id
                :placeholder (text :t.actions/comment)
                :value comment
                :on-change #(on-comment (.. % -target -value))}]]))

(defn public-checkbox-view [{:keys [id public on-set-public]}]
  (let [id (str "public-" id)]
    [:div.form-group
     [:div.form-check
      [:input.form-check-input {:type "checkbox"
                                :id id
                                :name id
                                :checked public
                                :on-change #(on-set-public (.. % -target -checked))}]
      [:label.form-check-label {:for id}
       (text :t.actions/remark-public)]]]))

(rf/reg-sub ::comment (fn [db [_ field-key]] (get-in db [::comment field-key])))
(rf/reg-event-db ::set-comment (fn [db [_ field-key value]] (assoc-in db [::comment field-key] value)))

(rf/reg-sub ::comment-public (fn [db [_ field-key]] (get-in db [::comment-public field-key])))
(rf/reg-event-db ::set-comment-public (fn [db [_ field-key value]] (assoc-in db [::comment-public field-key] value)))

(defn comment-field [{:keys [field-key label public-checkbox?]}]
  [:<>
   [comment-field-view {:id field-key
                        :label label
                        :comment @(rf/subscribe [::comment field-key])
                        :on-comment #(rf/dispatch [::set-comment field-key %])}]
   (when public-checkbox?
     [public-checkbox-view {:id field-key
                            :public @(rf/subscribe [::comment-public field-key])
                            :on-set-public #(rf/dispatch [::set-comment-public field-key %])}])])

;; attachments in suitable format for api:
(rf/reg-sub ::attachments (fn [db [_ field-key]] (mapv #(select-keys % [:attachment/id]) (get-in db [::attachments field-key]))))
;; attachments with filenames for rendering:
(rf/reg-sub ::attachments-with-filenames (fn [db [_ field-key]] (get-in db [::attachments field-key])))
(rf/reg-event-db ::set-attachments (fn [db [_ field-key value]] (assoc-in db [::attachments field-key] value)))
(rf/reg-event-db ::add-attachment (fn [db [_ field-key value]] (update-in db [::attachments field-key] conj value)))
(rf/reg-event-db
 ::remove-attachment
 (fn [db [_ field-key id]]
   (update-in db [::attachments field-key] (partial remove (comp #{id} :attachment/id)))))

(rf/reg-event-fx
 ::save-attachment
 (fn [_ [_ application-id field-key file]]
   (let [description [text :t.form/upload]]
     (post! "/api/applications/add-attachment"
            {:url-params {:application-id application-id}
             :body file
             :handler (flash-message/default-success-handler
                       :actions
                       description
                       (fn [response]
                         (rf/dispatch [::add-attachment field-key {:attachment/id (:id response)
                                                                   :attachment/filename (.. file (get "file") -name)}])))
             :error-handler (fn [response]
                              (if (= 415 (:status response))
                                (flash-message/show-default-error! :actions description
                                                                   [:div
                                                                    [:p [text :t.form/invalid-attachment]]
                                                                    [:p [text :t.form/upload-extensions]
                                                                     ": "
                                                                     attachment-types/allowed-extensions-string]])
                                ((flash-message/default-error-handler :actions description) response)))})
     {})))

(defn action-attachment [{:keys [application-id field-key]}]
  [fields/multi-attachment-view {:id field-key
                                 :attachments @(rf/subscribe [::attachments-with-filenames field-key])
                                 :on-attach #(rf/dispatch [::save-attachment application-id field-key %])
                                 :on-remove-attachment #(rf/dispatch [::remove-attachment field-key %])}])

(fetcher/reg-fetcher ::reviewers "/api/applications/reviewers" {:result (partial map enrich-user)})
(fetcher/reg-fetcher ::deciders "/api/applications/deciders" {:result (partial map enrich-user)})

(rf/reg-sub ::users (fn [db [_ field-key]] (get-in db [::users field-key])))
(rf/reg-event-db ::set-users (fn [db [_ field-key value]] (assoc-in db [::users field-key] value)))

(defn user-selection [{:keys [subscription field-key]}]
  (let [id (str field-key "-users")]
    [:div.form-group
     [:label {:for id} (text :t.actions/request-selections)]
     [dropdown/dropdown
      {:id id
       :items @(rf/subscribe subscription)
       :item-key :userid
       :item-label :display
       :item-selected? (set @(rf/subscribe [::users field-key]))
       :multi? true
       :on-change #(rf/dispatch [::set-users field-key %])}]]))

(defn action-form-view
  "Renders an action form that is collapsible.

  `id` - the id of the form
  `title` - the name of the action
  `content` - content of the form
  `buttons` - the actions that can be executed
  `:collapse-id` - optionally the collapse group the action is part of"
  [id title buttons content & [{:keys [collapse-id]}]]
  [:div.collapse {:id (action-collapse-id id)
                  :data-parent (if collapse-id (str "#" collapse-id) "#actions-forms")
                  :tab-index "-1"
                  :ref (fn [elem]
                         (when elem
                           (.on (js/$ elem) "shown.bs.collapse" #(.focus elem))))}
   [:h3.mt-5 title]
   content
   (into [:div.col.commands [cancel-action-button id]] buttons)])

(defn action-button [{:keys [id text class on-click]}]
  [:button.btn
   {:type :button
    :id (action-button-id id)
    :class (str (or class "btn-secondary")
                " btn-opens-more")
    :data-toggle "collapse"
    :data-target (str "#" (action-collapse-id id))
    :on-click on-click}
   text])

(defn command! [command params {:keys [description collapse on-finished]}]
  (assert (qualified-keyword? command)
          (pr-str command))
  (post! (str "/api/applications/" (name command))
         {:params params
          :handler (flash-message/default-success-handler
                    :actions
                    description
                    (fn [_]
                      (collapse-action-form collapse)
                      (on-finished)))
          :error-handler (flash-message/default-error-handler :actions description)}))

(defn guide []
  [:div])
