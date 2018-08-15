(ns rems.administration.license
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! fetch put!]]))

(defn- create-license [form]
  ; TODO: create API
  (put! "/api/licenses/create" {:params {:form form}
                                :handler (fn [resp]
                                           (dispatch! "#/administration"))}))

; TODO: group fields

(rf/reg-event-fx
  ::create-license
  (fn [db [_ form]]
    (create-license form)
    {}))

(rf/reg-event-db
  ::reset-create-license
  (fn [db _]
    (dissoc db ::title ::type ::link ::text)))

(rf/reg-sub
  ::form
  (fn [db _]
    (::form db)))
(rf/reg-event-db
  ::set-form-field
  (fn [db [_ keys value]]
    (assoc-in db (concat [::form] keys) value)))


;;;; UI ;;;;

(defn- save-license-button []
  (let [form @(rf/subscribe [::form])]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-license form])
      :disabled (not (and (not (str/blank? (:title (:default form))))
                          (not (str/blank? (:type form)))
                          (not (str/blank? (if (= "link" (:type form))
                                             (:link (:default form))
                                             (:text (:default form)))))))}
     (text :t.create-resource/save)]))                      ; TODO: rename translation key

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration")}
   (text :t.create-catalogue-item/cancel)])                 ; TODO: rename translation key

(defn create-license-page []
  (let [form (rf/subscribe [::form])]
    (fn []
      ; TODO: translations
      [collapsible/component
       {:id "create-license"
        :title (text :t.navigation/create-license)
        :always [:div
                 [:div.form-group.field
                  [:label {:for "default-title"} "Title"]
                  [:input.form-control {:type "text"
                                        :id "default-title"
                                        :name "default-title"
                                        :value (:title (:default @form))
                                        :on-change #(rf/dispatch [::set-form-field [:default :title] (.. % -target -value)])}]]
                 [:div.form-group.field
                  (let [value "link"]
                    [:div.form-check.form-check-inline
                     [:input.form-check-input {:type "radio"
                                               :id "link-type-field"
                                               :name "type-field"
                                               :value value
                                               :checked (= value (:type @form))
                                               :on-change #(when (.. % -target -checked)
                                                             (rf/dispatch [::set-form-field [:type] value]))}]
                     [:label.form-check-label {:for "link-type-field"} "External link"]])
                  (let [value "text"]
                    [:div.form-check.form-check-inline
                     [:input.form-check-input {:type "radio"
                                               :id "text-type-field"
                                               :name "type-field"
                                               :value value
                                               :checked (= value (:type @form))
                                               :on-change #(when (.. % -target -checked)
                                                             (rf/dispatch [::set-form-field [:type] value]))}]
                     [:label.form-check-label {:for "text-type-field"} "Inline text"]])]
                 (when (= "link" (:type @form))
                   [:div.form-group.field
                    [:label {:for "link-field"} "Link to the license"]
                    [:input.form-control {:type "text"
                                          :id "link-field"
                                          :name "link-field"
                                          :placeholder "https://example.com/license"
                                          :value (:link (:default @form))
                                          :on-change #(rf/dispatch [::set-form-field [:default :link] (.. % -target -value)])}]])
                 (when (= "text" (:type @form))
                   [:div.form-group.field
                    [:label {:for "text-field"} "License text"]
                    [:textarea.form-control {:type "text"
                                             :id "text-field"
                                             :name "text-field"
                                             :value (:text (:default @form))
                                             :on-change #(rf/dispatch [::set-form-field [:default :text] (.. % -target -value)])}]])
                 ; TODO: non-default languages
                 ; https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes
                 [:div.col.commands
                  [cancel-button]
                  [save-license-button]]]}])))
