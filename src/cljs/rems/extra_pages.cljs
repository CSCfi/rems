(ns rems.extra-pages
  (:require [markdown.core :as md]
            [medley.core :refer [find-first]]
            [re-frame.core :as rf]
            [rems.atoms :refer [document-title] :as atoms]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [fetch]]))

;;;; State

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ page-id]]
   (fetch (str "/api/extra-pages/" page-id)
          {:handler #(rf/dispatch [::fetch-extra-page-result %])
           :error-handler #(rf/dispatch [::fetch-extra-page-result :not-found])})
   {:db (assoc db
               ::page-id page-id
               ::loading? true)}))

(rf/reg-event-db
 ::fetch-extra-page-result
 (fn [db [_ extra-page]]
   (-> db
       (assoc ::content extra-page)
       (dissoc ::loading?))))

(rf/reg-sub ::page-id (fn [db _] (::page-id db)))
(rf/reg-sub ::content (fn [db _] (::content db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-sub
 ::page
 (fn [db _]
   (let [page-id (::page-id db)]
     (->> db
          :config
          :extra-pages
          (filter #(= page-id (:id %)))
          first))))

(rf/reg-sub
 ::title
 (fn [_ _]
   [(rf/subscribe [::page]) (rf/subscribe [:language])])
 (fn [[page language] _]
   (->> page
        :translations
        language
        :title)))

;;;; Entry point

(defn extra-pages []
  (let [loading? @(rf/subscribe [::loading?])
        page-id @(rf/subscribe [::page-id])
        title @(rf/subscribe [::title])
        extra-page @(rf/subscribe [::content])
        extra-pages (:extra-pages @(rf/subscribe [:rems.config/config]))
        config-extra-page (find-first (comp #{page-id} :id) extra-pages)
        language @(rf/subscribe [:language])]
    [:div
     [document-title title {:heading? (get config-extra-page :heading true)}]
     [flash-message/component :top]
     (if loading?
       [spinner/big]

       (if (= extra-page :not-found)
         (rf/dispatch [:set-active-page :not-found])

         (if-let [content (get extra-page language)]
           [:div.document
            (if content
              {:dangerouslySetInnerHTML {:__html (md/md->html content)}}
              (text :t/missing))]

           ;; if no file content for this page exists, we can try URL
           (if-let [url (get-in config-extra-page [:translations language :url] (:url config-extra-page))]
             [:div.m-3 [atoms/link nil url url]]
             (rf/dispatch [:set-active-page :not-found])))))]))
