(ns rems.extra-pages
  (:require [better-cond.core :as b]
            [markdown.core :as md]
            [medley.core :refer [find-first]]
            [re-frame.core :as rf]
            [rems.atoms :refer [document-title] :as atoms]
            [rems.config]
            [rems.flash-message :as flash-message]
            [rems.globals]
            [rems.spinner :as spinner]
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

(rf/reg-sub ::page-id (fn [db] (::page-id db)))
(rf/reg-sub ::content (fn [db] (::content db)))
(rf/reg-sub ::loading? (fn [db] (::loading? db)))

(rf/reg-sub ::page :<- [::page-id] (fn [page-id]
                                     (->> (:extra-pages @rems.globals/config)
                                          (find-first (comp #{page-id} :id)))))

;;;; Entry point

(defn extra-pages []
  (let [page-config @(rf/subscribe [::page])
        language @rems.config/language-or-default
        title (get-in page-config [:translations language :title])
        heading? (get page-config :heading true)]
    [:div
     [document-title title {:heading? heading?}]
     [flash-message/component :top]

     (b/cond
       @(rf/subscribe [::loading?])
       [spinner/big]

       :let [page @(rf/subscribe [::content])
             page-content (get page language)
             url (get-in page-config [:translations language :url] (:url page-config))]

       (= page :not-found)
       (rf/dispatch [:set-active-page :not-found])

       (some? page-content)
       [:div.document {:dangerouslySetInnerHTML {:__html (md/md->html page-content)}}]

       (some? url) ; if no file content for this page exists, we can try URL
       [:div.m-3 [atoms/link nil url url]]

       :else
       (rf/dispatch [:set-active-page :not-found]))]))
