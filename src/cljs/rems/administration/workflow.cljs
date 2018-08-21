(ns rems.administration.workflow
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text localize-item]]
            [rems.util :refer [dispatch! fetch post!]]))

(def actor-role-approver "approver")
(def actor-role-reviewer "text")

(defn build-request [form]
  ; TODO
  form)

(defn- create-workflow [form]
  (post! "/api/licenses/create" {:params (build-request form)
                                 :handler (fn [resp]
                                            (dispatch! "#/administration"))}))

(rf/reg-event-fx
  ::create-workflow
  (fn [_ [_ form]]
    (create-workflow form)
    {}))

(rf/reg-event-db
  ::reset-create-workflow
  (fn [db _]
    (assoc db ::form {:rounds [{}]})))

(rf/reg-sub
  ::form
  (fn [db _]
    (::form db)))

(rf/reg-event-db
  ::set-form-field
  (fn [db [_ keys value]]
    (assoc-in db (concat [::form] keys) value)))


;;;; UI ;;;;

(defn- workflow-title-field []
  (let [form @(rf/subscribe [::form])
        keys [:title]
        id "title"]
    [:div.form-group.field
     [:label {:for id} "Title"]                             ; TODO: translation
     [:input.form-control {:type "text"
                           :id id
                           :value (get-in form keys)
                           :on-change #(rf/dispatch [::set-form-field keys (.. % -target -value)])}]]))

(defn- round-type-radio-button [round value label]
  (let [form @(rf/subscribe [::form])
        keys [:rounds round :type]
        id (str "round-" round "-type-" value)]
    [:div.form-check.form-check-inline
     [:input.form-check-input {:type "radio"
                               :id id
                               :value value
                               :checked (= value (get-in form keys))
                               :on-change #(when (.. % -target -checked)
                                             (rf/dispatch [::set-form-field keys value]))}]
     [:label.form-check-label {:for id} label]]))

(defn- round-type-radio-group [round]
  [:div.form-group.field
   [round-type-radio-button round actor-role-approver "Approval round"] ; TODO: translation
   [round-type-radio-button round actor-role-reviewer "Review round"]]) ; TODO: translation

(defn- workflow-actors-field [round]
  (let [form @(rf/subscribe [::form])
        keys [:rounds round :actors]
        id (str "round-" round "-actors")]
    [:div.form-group.field
     [:label {:for id} "Users"]                             ; TODO: translation
     [:input.form-control {:type "text"
                           :id id
                           :value (get-in form keys)
                           :on-change #(rf/dispatch [::set-form-field keys (.. % -target -value)])}]]))

(defn- add-round-button []
  (let [form @(rf/subscribe [::form])]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::set-form-field [:rounds (count (:rounds form))] {}])}
     "Add round"]))                                         ; TODO: translation

(defn- save-workflow-button []
  (let [form @(rf/subscribe [::form])]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-workflow form])
      :disabled (not (build-request form))}
     (text :t.administration/save)]))

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration")}
   (text :t.administration/cancel)])

(defn create-workflow-page []
  (let [form @(rf/subscribe [::form])]
    [collapsible/component
     {:id "create-workflow"
      :title "Create workflow"                              ; TODO: translation
      :always [:div
               [workflow-title-field]
               (doall (for [round (range (count (:rounds form)))]
                        [:div
                         {:key round}
                         [:h2 (str "Round " (inc round))]
                         [round-type-radio-group round]
                         [workflow-actors-field round]]))

               [:div.col.commands
                [add-round-button]
                [cancel-button]
                [save-workflow-button]]]}]))
