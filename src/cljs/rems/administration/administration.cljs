(ns rems.administration.administration
  (:require [re-frame.core :as rf]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch!]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::loading? false)}))

(rf/reg-sub
 ::loading?
 (fn [db _]
   (::loading? db)))

(defn- to-administration [class]
  [:a {:class class
       :href "/#/administration "}
   (text :t.navigation/administration)])

(defn- to-catalogue-items [class]
  [:a {:class class
       :href "/#/administration/catalogue-items"}
   (text :t.administration/catalogue-items)])

(defn- to-resources [class]
  [:a {:class class
       :href "/#/administration/resources"}
   (text :t.administration/resources)])

(defn- to-forms [class]
  [:a {:class class
       :href "/#/administration/forms"}
   (text :t.administration/forms)])

(defn- to-workflows [class]
  [:a {:class class
       :href "/#/administration/workflows"}
   (text :t.administration/workflows)])

(defn- to-licenses [class]
  [:a {:class class
       :href "/#/administration/licenses"}
   (text :t.administration/licenses)])

(defn administration-navigator [selected]
  [:div.navbar.mb-4
   [to-administration [:nav-item :nav-link (when (= selected :administration) :active)]]
   [to-catalogue-items [:nav-item :nav-link (when (= selected :rems.administration/catalogue-items) :active)]]
   [to-resources [:nav-item :nav-link (when (= selected :rems.administration/resources) :active)]]
   [to-forms [:nav-item :nav-link (when (= selected :rems.administration/forms) :active)]]
   [to-workflows [:nav-item :nav-link (when (= selected :rems.administration/workflows) :active)]]
   [to-licenses [:nav-item :nav-link (when (= selected :rems.administration/licenses) :active)]]])

(defn administration-navigator-container
  "Component for showing a navigator in the administration pages.

  Subscribes to current page to show the link as selected. The pure functional version is `administration-navigator`"
  []
  (let [page (rf/subscribe [:page])]
    [administration-navigator @page]))

(defn administration-page []
  (let [loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration-navigator]
       [:h2 (text :t.navigation/administration)]
       (if @loading?
         [spinner/big]
         [:div.spaced-sections
          {:style {:display :flex
                   :flex-direction :column
                   :max-width "15rem"}}
          [to-catalogue-items [:btn :btn-primary]]
          [to-resources [:btn :btn-primary]]
          [to-forms [:btn :btn-primary]]
          [to-workflows [:btn :btn-primary]]
          [to-licenses [:btn :btn-primary]]])])))


(defn guide []
  [:div
   (component-info administration-navigator)
   (example "administration-navigator with resources selected"
            [administration-navigator :rems.administration/resources])])
