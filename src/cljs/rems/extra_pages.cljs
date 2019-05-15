(ns rems.extra-pages
  (:require [markdown.core :as md]
            [re-frame.core :as rf]
            [reagent.core :as reagent]
            [rems.atoms :as atoms]
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

(defn use-h1-as-title! [element]
  (let [h1 (when element (.querySelector element "h1"))
        title (when h1 (.-innerText h1))]
    (atoms/set-document-title! title)))

(defn extra-pages []
  (let [element (atom nil)]
    (reagent/create-class
     {:component-did-mount #(use-h1-as-title! @element)
      :component-did-update #(use-h1-as-title! @element)
      :display-name "extra-pages"
      :reagent-render
      (fn []
        (let [loading? @(rf/subscribe [::loading?])
              extra-page @(rf/subscribe [::extra-page])
              language @(rf/subscribe [:language])]
          (if loading?
            [spinner/big]
            (if (= extra-page :not-found)
              (rf/dispatch [:set-active-page :not-found])
              (let [content (get extra-page language)]
                [:div.container
                 [:div.row
                  [:div.col-md-12
                   [:div.document
                    (if content
                      {:dangerouslySetInnerHTML {:__html
                                                 (md/md->html content)}
                       :ref #(reset! element %)}
                      (text :t/missing))]]]])))))})))
