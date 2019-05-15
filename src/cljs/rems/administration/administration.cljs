(ns rems.administration.administration
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [document-title]]
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
  [:div.navbar.mb-4.mr-auto.ml-auto
   [to-administration [:nav-item :nav-link (when (contains? #{:rems.administration/administration}
                                                            selected)
                                             :active)]]
   [to-catalogue-items [:nav-item :nav-link (when (contains? #{:rems.administration/catalogue-items
                                                               :rems.administration/catalogue-item
                                                               :rems.administration/create-catalogue-item}
                                                             selected)
                                              :active)]]
   [to-resources [:nav-item :nav-link (when (contains? #{:rems.administration/resources
                                                         :rems.administration/resource
                                                         :rems.administration/create-resource}
                                                       selected)
                                        :active)]]
   [to-forms [:nav-item :nav-link (when (contains? #{:rems.administration/forms
                                                     :rems.administration/form
                                                     :rems.administration/create-form}
                                                   selected)
                                    :active)]]
   [to-workflows [:nav-item :nav-link (when (contains? #{:rems.administration/workflows
                                                         :rems.administration/workflow
                                                         :rems.administration/create-workflow}
                                                       selected)
                                        :active)]]
   [to-licenses [:nav-item :nav-link (when (contains? #{:rems.administration/licenses
                                                        :rems.administration/license
                                                        :rems.administration/create-license}
                                                      selected)
                                       :active)]]])

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
       [administration-navigator-container]
       [document-title (text :t.navigation/administration)]
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
