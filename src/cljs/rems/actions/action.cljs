(ns rems.actions.action
  (:require [rems.atoms :refer [textarea]]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

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
  (post! (str "/api/applications/" command)
         {:params params
          :handler (flash-message/default-success-handler
                    :actions
                    description
                    (fn [_]
                      (collapse-action-form collapse)
                      (on-finished)))
          :error-handler (flash-message/default-error-handler :actions description)}))
