(ns rems.administration.license
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! fetch put!]]))

(defn- create-license [title type link text]
  ; TODO: create API
  (put! "/api/licenses/create" {:params {:title title
                                         :type type
                                         :link link
                                         :text text}
                                :handler (fn [resp]
                                           (dispatch! "#/administration"))}))

; TODO: group fields

(rf/reg-event-fx
  ::create-license
  (fn [db [_ title type link text]]
    (create-license title type link text)
    {}))

(rf/reg-event-db
  ::reset-create-license
  (fn [db _]
    (dissoc db ::title ::type ::link ::text)))

(rf/reg-sub
  ::title
  (fn [db _]
    (::title db)))
(rf/reg-event-db
  ::set-title
  (fn [db [_ title]]
    (assoc db ::title title)))

(rf/reg-sub
  ::type
  (fn [db _]
    (::type db)))
(rf/reg-event-db
  ::set-type
  (fn [db [_ type]]
    (assoc db ::type type)))

(rf/reg-sub
  ::link
  (fn [db _]
    (::link db)))
(rf/reg-event-db
  ::set-link
  (fn [db [_ type]]
    (assoc db ::link type)))

(rf/reg-sub
  ::text
  (fn [db _]
    (::text db)))
(rf/reg-event-db
  ::set-text
  (fn [db [_ type]]
    (assoc db ::text type)))


;;;; UI ;;;;

(defn- save-license-button []
  (let [title-field @(rf/subscribe [::title])
        type-field @(rf/subscribe [::type])
        link-field @(rf/subscribe [::link])
        text-field @(rf/subscribe [::text])]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-license title-field type-field link-field text-field])
      :disabled (not (and (not (str/blank? title-field))
                          (not (str/blank? type-field))
                          (not (str/blank? (if (= "link" type-field)
                                             link-field
                                             text-field)))))}
     (text :t.create-resource/save)]))                      ; TODO: rename translation key

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration")}
   (text :t.create-catalogue-item/cancel)])                 ; TODO: rename translation key

(defn create-license-page []
  (let [title-field (rf/subscribe [::title])
        type-field (rf/subscribe [::type])
        link-field (rf/subscribe [::link])
        text-field (rf/subscribe [::text])]
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
                                        :value @title-field
                                        :on-change #(rf/dispatch [::set-title (.. % -target -value)])}]]
                 [:div.form-group.field
                  (let [value "link"]
                    [:div.form-check.form-check-inline
                     [:input.form-check-input {:type "radio"
                                               :id "link-type-field"
                                               :name "type-field"
                                               :value value
                                               :checked (= value @type-field)
                                               :on-change #(when (.. % -target -checked)
                                                             (rf/dispatch [::set-type value]))}]
                     [:label.form-check-label {:for "link-type-field"} "External link"]])
                  (let [value "text"]
                    [:div.form-check.form-check-inline
                     [:input.form-check-input {:type "radio"
                                               :id "text-type-field"
                                               :name "type-field"
                                               :value value
                                               :checked (= value @type-field)
                                               :on-change #(when (.. % -target -checked)
                                                             (rf/dispatch [::set-type value]))}]
                     [:label.form-check-label {:for "text-type-field"} "Inline text"]])]
                 (when (= "link" @type-field)
                   [:div.form-group.field
                    [:label {:for "link-field"} "Link to the license"]
                    [:input.form-control {:type "text"
                                          :id "link-field"
                                          :name "link-field"
                                          :placeholder "https://example.com/license"
                                          :value @link-field
                                          :on-change #(rf/dispatch [::set-link (.. % -target -value)])}]])
                 (when (= "text" @type-field)
                   [:div.form-group.field
                    [:label {:for "text-field"} "License text"]
                    [:textarea.form-control {:type "text"
                                             :id "text-field"
                                             :name "text-field"
                                             :value @text-field
                                             :on-change #(rf/dispatch [::set-text (.. % -target -value)])}]])
                 ; TODO: non-default languages
                 ; https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes
                 [:div.col.commands
                  [cancel-button]
                  [save-license-button]]]}])))
