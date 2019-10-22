(ns rems.administration.administration
  (:require [re-frame.core :as rf]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::loading? false)}))

(rf/reg-sub
 ::loading?
 (fn [db _]
   (::loading? db)))

(defn- nav-classes [active?]
  (into [:nav-item :nav-link]
        (when active?
          [:active])))

(defn- to-administration [active?]
  [atoms/link {:class (nav-classes active?)} "/administration" (text :t.navigation/administration)])

(defn- to-catalogue-items [active?]
  [atoms/link {:class (nav-classes active?)} "/administration/catalogue-items" (text :t.administration/catalogue-items)])

(defn- to-resources [active?]
  [atoms/link {:class (nav-classes active?)} "/administration/resources" (text :t.administration/resources)])

(defn- to-forms [active?]
  [atoms/link {:class (nav-classes active?)} "/administration/forms" (text :t.administration/forms)])

(defn- to-workflows [active?]
  [atoms/link {:class (nav-classes active?)} "/administration/workflows" (text :t.administration/workflows)])

(defn- to-licenses [active?]
  [atoms/link {:class (nav-classes active?)} "/administration/licenses" (text :t.administration/licenses)])

(defn- to-blacklist [active?]
  [atoms/link {:class (nav-classes active?)} "/administration/blacklist" (text :t.administration/blacklist)])

(defn administration-navigator [selected]
  [:div.navbar.mb-4.mr-auto.ml-auto
   [to-administration (contains? #{:rems.administration/administration} selected)]
   [to-catalogue-items (contains? #{:rems.administration/catalogue-items
                                    :rems.administration/catalogue-item
                                    :rems.administration/create-catalogue-item}
                                  selected)]
   [to-resources (contains? #{:rems.administration/resources
                              :rems.administration/resource
                              :rems.administration/create-resource}
                            selected)]
   [to-forms (contains? #{:rems.administration/forms
                          :rems.administration/form
                          :rems.administration/create-form}
                        selected)]
   [to-workflows (contains? #{:rems.administration/workflows
                              :rems.administration/workflow
                              :rems.administration/create-workflow}
                            selected)]
   [to-licenses (contains? #{:rems.administration/licenses
                             :rems.administration/license
                             :rems.administration/create-license}
                           selected)]
   [to-blacklist (contains? #{:rems.administration/blacklist}
                            selected)]])

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
       [flash-message/component :top]
       (if @loading?
         [spinner/big]
         (text :t.administration/intro))])))


(defn guide []
  [:div
   (component-info administration-navigator)
   (example "administration-navigator with resources selected"
            [administration-navigator :rems.administration/resources])])
