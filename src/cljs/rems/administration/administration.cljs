(ns rems.administration.administration
  (:require [re-frame.core :as rf]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::loading? false)}))

(rf/reg-sub
 ::loading?
 (fn [db _]
   (::loading? db)))

(defn- to-create-catalogue-item []
  [:a.btn.btn-secondary
   {:href "/#/create-catalogue-item"}
   (text :t.administration/create-catalogue-item)])

(defn- to-create-form []
  [:a.btn.btn-secondary
   {:href "/#/create-form"}
   (text :t.administration/create-form)])

(defn- to-create-license []
  [:a.btn.btn-secondary
   {:href "/#/create-license"}
   (text :t.administration/create-license)])

(defn- to-create-resource []
  [:a.btn.btn-secondary
   {:href "/#/create-resource"}
   (text :t.administration/create-resource)])

(defn- to-create-workflow []
  [:a.btn.btn-secondary
   {:href "/#/create-workflow"}
   (text :t.administration/create-workflow)])

(defn- to-catalogue-items []
  [:a.btn.btn-primary {:href "/#/administration/catalogue-items"}
   (text :t.administration/catalogue-items)])

(defn- to-resources []
  [:a.btn.btn-primary {:href "/#/administration/resources"}
   (text :t.administration/resources)])

(defn- to-forms []
  [:a.btn.btn-primary {:href "/#/administration/forms"}
   (text :t.administration/forms)])

(defn- to-workflows []
  [:a.btn.btn-primary {:href "/#/administration/workflows"}
   (text :t.administration/workflows)])

(defn- to-licenses []
  [:a.btn.btn-primary {:href "/#/administration/licenses"}
   (text :t.administration/licenses)])

(defn administration-page []
  (let [loading? (rf/subscribe [::loading?])]
    (fn []
      [:div [:h2 (text :t.navigation/administration)]
       (if @loading?
         [spinner/big]
         [:div.spaced-sections
          {:style {:display :flex
                   :flex-direction :column
                   :max-width "15rem"}}
          [to-catalogue-items]
          [to-resources]
          [to-forms]
          [to-workflows]
          [to-licenses]])])))
