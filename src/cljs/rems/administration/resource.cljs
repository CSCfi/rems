(ns rems.administration.resource
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.autocomplete :as autocomplete]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! fetch put!]]))

(defn- fetch-licenses []
  (fetch "/api/licenses?active=true"
         {:handler #(do (rf/dispatch [::set-licenses %])
                        (rf/dispatch [::set-selected-licenses #{}]))}))

(defn- create-resource [prefix resid licenses]
  (put! "/api/resources/create" {:params {:prefix prefix
                                          :resid resid
                                          :licenses (if licenses
                                                      (map :id licenses)
                                                      [])}
                                 :handler (fn [resp]
                                            (dispatch! "#/administration"))}))

(rf/reg-sub
 ::prefix
 (fn [db _]
   (::prefix db)))

(rf/reg-event-db
 ::set-prefix
 (fn [db [_ prefix]]
   (assoc db ::prefix prefix)))

(rf/reg-sub
 ::resid
 (fn [db _]
   (::resid db)))

(rf/reg-event-db
 ::set-resid
 (fn [db [_ resid]]
   (assoc db ::resid resid)))

(rf/reg-sub
 ::licenses
 (fn [db _]
   (::licenses db)))

(rf/reg-sub
 ::selected-licenses
 (fn [db _]
  (::selected-licenses db)))

(rf/reg-event-db
 ::set-licenses
 (fn [db [_ licenses]]
   (assoc db ::licenses licenses)))

(rf/reg-event-db
 ::set-selected-licenses
 (fn [db [_ licenses]]
   (assoc db ::selected-licenses licenses)))

(rf/reg-event-db
 ::add-selected-licenses
 (fn [db [_ license]]
   (if (contains? (::selected-licenses db) license)
     db
     (update db ::selected-licenses conj license))))

(rf/reg-event-db
 ::remove-selected-licenses
 (fn [db [_ license]]
   (update db ::selected-licenses disj license)))

(rf/reg-event-fx
 ::create-resource
 (fn [db [_ prefix resid licenses]]
   (create-resource prefix resid licenses)
   {}))

(rf/reg-event-db
 ::reset-create-resource
 (fn [db _]
   (dissoc db ::prefix ::resid ::selected-licenses)))

;;;; UI ;;;;

(defn- save-resource-button []
  (let [prefix @(rf/subscribe [::prefix])
        resid @(rf/subscribe [::resid])
        licenses @(rf/subscribe [::selected-licenses])]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-resource prefix resid licenses])
      :disabled (not (and (not (str/blank? prefix)) (not (str/blank? resid))))}
     (text :t.create-resource/save)]))

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration")}
   (text :t.create-catalogue-item/cancel)])

(defn create-resource-page []
  (fetch-licenses)
  (let [prefix (rf/subscribe [::prefix])
        resid (rf/subscribe [::resid])
        licenses (rf/subscribe [::licenses])
        selected-licenses (rf/subscribe [::selected-licenses])]
    (fn []
      [collapsible/component
       {:id "create-create"
        :title (text :t.navigation/create-resource)
        :always [:div
                 [:div.form-group.field
                  [:label {:for "prefix"} (text :t.create-resource/prefix)]
                  [:input.form-control {:name "prefix"
                                        :type :text
                                        :placeholder (text :t.create-resource/prefix-placeholder)
                                        :value @prefix
                                        :on-change #(rf/dispatch [::set-prefix (.. % -target -value)])}]]
                 [:div.form-group.field
                  [:label {:for "resid"} (text :t.create-resource/resid)]
                  [:input.form-control {:name "resid"
                                        :type :text
                                        :placeholder (text :t.create-resource/resid-placeholder)
                                        :value @resid
                                        :on-change #(rf/dispatch [::set-resid (.. % -target -value)])}]]
                 [:div.form-group
                  [:label (text :t.create-resource/licenses-selection)]
                  [autocomplete/component
                   {:value (sort-by :id @selected-licenses)
                    :items @licenses
                    :value->text #(:title %2)
                    :item->key :id
                    :item->text :title
                    :item->value identity
                    :search-fields [:title]
                    :add-fn #(rf/dispatch [::add-selected-licenses %])
                    :remove-fn #(rf/dispatch [::remove-selected-licenses %])}]]
                 [:div.col.commands
                  [cancel-button]
                  [save-resource-button]]]}])))
