(ns rems.actions.action
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [attachment-link close-symbol success-symbol textarea]]
            [rems.common.attachment-types :as attachment-types]
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

(defn action-comment [{:keys [id label comment on-comment]}]
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

;; attachments in suitable format for api:
(rf/reg-sub ::attachments (fn [db [_ key]] (mapv #(select-keys % [:attachment/id]) (get-in db [::attachments key]))))
;; attachments with filenames for rendering:
(rf/reg-sub ::attachments-with-filenames (fn [db [_ key]] (get-in db [::attachments key])))
(rf/reg-event-db ::set-attachments (fn [db [_ key value]] (assoc-in db [::attachments key] value)))
(rf/reg-event-db ::add-attachment (fn [db [_ key value]] (update-in db [::attachments key] conj value)))
(rf/reg-event-db
 ::remove-attachment
 (fn [db [_ key id]]
   (update-in db [::attachments key] (partial remove (comp #{id} :attachment/id)))))

(rf/reg-event-fx
 ::save-attachment
 (fn [{:keys [db]} [_ application-id key file]]
   (let [description [text :t.form/upload]]
     (post! "/api/applications/add-attachment"
            {:url-params {:application-id application-id}
             :body file
             :handler (flash-message/default-success-handler
                       :actions
                       description
                       (fn [response]
                         (rf/dispatch [::add-attachment key {:attachment/id (:id response)
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

(defn action-attachment [{:keys [application-id key]}]
  [fields/multi-attachment-view {:key key
                                 :attachments @(rf/subscribe [::attachments-with-filenames key])
                                 :on-attach #(rf/dispatch [::save-attachment application-id key %])
                                 :on-remove-attachment #(rf/dispatch [::remove-attachment key %])}])

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
