(ns rems.administration.catalogue-item
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [document-title readonly-checkbox]]
            [rems.collapsible :as collapsible]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [get-localized-title localize-time localized text]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ catalogue-item-id]]
   {:db (assoc db ::loading? true)
    ::fetch-catalogue-item [catalogue-item-id]}))

(defn- fetch-catalogue-item [catalogue-item-id]
  (fetch (str "/api/catalogue-items/" catalogue-item-id)
         {:handler #(rf/dispatch [::fetch-catalogue-item-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch catalogue items")}))

(rf/reg-fx ::fetch-catalogue-item (fn [[catalogue-item-id]] (fetch-catalogue-item catalogue-item-id)))

(rf/reg-event-db
 ::fetch-catalogue-item-result
 (fn [db [_ catalogue-item]]
   (-> db
       (assoc ::catalogue-item catalogue-item)
       (dissoc ::loading?))))

(rf/reg-sub ::catalogue-item (fn [db _] (::catalogue-item db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn edit-action [catalogue-item-id]
  (atoms/edit-action
   {:class "edit-catalogue-item"
    :url (str "/administration/catalogue-items/edit/" catalogue-item-id)}))

(defn edit-button [catalogue-item-id]
  [atoms/action-button (edit-action catalogue-item-id)])

(defn- manage-categories-button []
  [atoms/link {:class "btn btn-primary"}
   "/administration/categories"
   (text :t.administration/manage-categories)])

(defn catalogue-item-view [catalogue-item]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "catalogue-item"
     :title [:span#title (get-localized-title catalogue-item)]
     :always (into [:div
                    [inline-info-field (text :t.administration/organization) (localized (get-in catalogue-item [:organization :organization/name]))]]
                   (concat
                    (for [[langcode localization] (:localizations catalogue-item)]
                      (let [suffix (str " (" (str/upper-case (name langcode)) ")")]
                        [:<>
                         [inline-info-field (str (text :t.administration/title) suffix)
                          (:title localization)]
                         [inline-info-field (str (text :t.catalogue/more-info) suffix)
                          (let [infourl (:infourl localization)]
                            (when-not (empty? infourl)
                              [:a {:href infourl :target :_blank} infourl " " [atoms/external-link]]))]]))
                    [[inline-info-field (text :t.administration/resource)
                      [atoms/link nil
                       (str "/administration/resources/" (:resource-id catalogue-item))
                       (:resource-name catalogue-item)]]
                     [inline-info-field (text :t.administration/workflow)
                      [atoms/link nil
                       (str "/administration/workflows/" (:wfid catalogue-item))
                       (:workflow-name catalogue-item)]]
                     [inline-info-field (text :t.administration/form)
                      (when (:formid catalogue-item)
                        [atoms/link nil
                         (str "/administration/forms/" (:formid catalogue-item))
                         (:form-name catalogue-item)])]
                     [inline-info-field (text :t.administration/categories)
                      (when-let [categories (:categories catalogue-item)]
                        (interpose
                         ", "
                         (doall
                          (for [cat categories]
                            ^{:key (:category/id cat)}
                            [atoms/link nil
                             (str "/administration/categories/" (:category/id cat))
                             (localized (:category/title cat))]))))]
                     [inline-info-field (text :t.administration/start) (localize-time (:start catalogue-item))]
                     [inline-info-field (text :t.administration/end) (localize-time (:end catalogue-item))]
                     [inline-info-field (text :t.administration/active) [readonly-checkbox {:value (status-flags/active? catalogue-item)}]]]))}]
   (let [id (:id catalogue-item)]
     [:div.col.commands
      [administration/back-button "/administration/catalogue-items"]
      [roles/show-when roles/+admin-write-roles+
       [edit-button id]
       [status-flags/enabled-toggle catalogue-item #(rf/dispatch [:rems.administration.catalogue-items/set-catalogue-item-enabled %1 %2 [::enter-page id]])]
       [status-flags/archived-toggle catalogue-item #(rf/dispatch [:rems.administration.catalogue-items/set-catalogue-item-archived %1 %2 [::enter-page id]])]]
      [manage-categories-button]])])

(defn catalogue-item-page []
  (let [catalogue-item (rf/subscribe [::catalogue-item])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration/navigator]
       [document-title (text :t.administration/catalogue-item)]
       [flash-message/component :top]
       (if @loading?
         [spinner/big]
         [catalogue-item-view @catalogue-item])])))
