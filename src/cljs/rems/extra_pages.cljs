(ns rems.extra-pages
  (:require [markdown.core :as md]
            [re-frame.core :as rf]
            [rems.text :refer [text]]
            [rems.util :refer [fetch]]
            [rems.spinner :as spinner]))

;;;; State

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ page-id]]
   (fetch (str "/api/extra-pages/" page-id)
          {:handler #(rf/dispatch [::fetch-extra-page-result %])})
   {:db (assoc db ::loading? true)}))

(rf/reg-event-db
 ::fetch-extra-page-result
 (fn [db [_ extra-page]]
   (-> db
       (assoc ::extra-page extra-page)
       (dissoc ::loading?))))

(rf/reg-sub ::extra-page (fn [db _] (::extra-page db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

;;;; Entry point

(defn extra-pages []
  (let [loading? @(rf/subscribe [::loading?])
        extra-page @(rf/subscribe [::extra-page])
        language @(rf/subscribe [:language])]
    (if loading?
      [spinner/big]
      (let [content (get extra-page language)]
        [:div.container
         [:div.row
          [:div.col-md-12
           [:div.document
            (if content
              {:dangerouslySetInnerHTML
               {:__html
                (md/md->html content)}}
              (text :t/missing))]]]]))))
