(ns rems.administration.components
  (:require [clojure.string :as str]
            [re-frame.core :as rf]))

; reusable form components

(defn- key-to-id [key]
  (if (number? key)
    (str key)
    (name key)))

(defn- keys-to-id [keys]
  (->> keys
       (map key-to-id)
       (str/join "-")))

(defn text-field [context {:keys [keys label placeholder]}]
  (let [form @(rf/subscribe [(:get-form context)])
        id (keys-to-id keys)]
    [:div.form-group.field
     [:label {:for id} label]
     [:input.form-control {:type "text"
                           :id id
                           :placeholder placeholder
                           :value (get-in form keys)
                           :on-change #(rf/dispatch [(:update-form context)
                                                     keys
                                                     (.. % -target -value)])}]]))

(defn- localized-text-field-lang [context {:keys [keys-prefix lang]}]
  (let [form @(rf/subscribe [(:get-form context)])
        keys (conj keys-prefix lang)
        id (keys-to-id keys)]
    [:div.form-group.row
     [:label.col-sm-1.col-form-label {:for id}
      (str/upper-case (name lang))]
     [:div.col-sm-11
      [:input.form-control {:type "text"
                            :id id
                            :value (get-in form keys)
                            :on-change #(rf/dispatch [(:update-form context)
                                                      keys
                                                      (.. % -target -value)])}]]]))

(defn localized-text-field [context {:keys [keys label]}]
  (let [languages @(rf/subscribe [:languages])]
    (into [:div.form-group.field
           [:label label]]
          (for [lang languages]
            [localized-text-field-lang context {:keys-prefix keys
                                                :lang lang}]))))

(defn checkbox [context {:keys [keys label]}]
  (let [form @(rf/subscribe [(:get-form context)])
        id (keys-to-id keys)]
    [:div.form-group.field
     [:div.form-check
      [:input.form-check-input {:id id
                                :type "checkbox"
                                :checked (boolean (get-in form keys))
                                :on-change #(rf/dispatch [(:update-form context)
                                                          keys
                                                          (.. % -target -checked)])}]
      [:label.form-check-label {:for id}
       label]]]))

(defn radio-button [context {:keys [keys value label]}]
  (let [form @(rf/subscribe [(:get-form context)])
        name (keys-to-id keys)
        id (keys-to-id (conj keys value))]
    [:div.form-check
     [:input.form-check-input {:id id
                               :type "radio"
                               :name name
                               :value value
                               :checked (= value (get-in form keys))
                               :on-change #(when (.. % -target -checked)
                                             (rf/dispatch [(:update-form context) keys value]))}]
     [:label.form-check-label {:for id}
      label]]))

(defn radio-button-group [context {:keys [keys options]}]
  (into [:div.form-group.field]
        (map (fn [{:keys [value label]}]
               [radio-button context {:keys keys
                                      :value value
                                      :label label}])
             options)))
