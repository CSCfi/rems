(ns rems.modal
  (:require [rems.guide-functions]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn- shade-wrapper [content on-close]
  [:div.modal--shade
   {:style {:position :fixed
            :z-index 10000
            :left 0
            :top 0
            :right 0
            :bottom 0
            :background-color "rgba(0,0,0,0.5)"
            :display :flex
            :justify-content :center
            :align-items :center}
    :on-click on-close}
   [:div {:style {:border-radius "0.25rem"
                  :min-width "50%"
                  :background-color "#fff"}
          :on-click (fn [e] (.stopPropagation e))}
    content]])

(defn component
  "Displays a modal dialog

  Pass a map of options with the following keys:
  `:title` component displayed in title area
  `:content` component displayed in content area
  `:commands` seq of components displayed in commands area
  `:on-close` triggers the function callback given as an argument when modal should be closed
  `:shade?` should the modal have a dark blocking shade behind it? Default true"
  [{:keys [title content commands on-close shade?]}]
  (let [content [collapsible/component
                 {:title [:div.modal--title {:style {:display :flex
                                                     :justify-content
                                                     :space-between
                                                     :align-items :center}}
                          title
                          [:a {:href "#"
                               :on-click on-close}
                           [:i.ml-3.fa.fa-times
                            {:style {:color "#ccc"}}]]]
                  :always [:div.full
                           [:div.modal--content content]
                           (into [:div.modal--commands.commands {:style {:padding 0}}]
                                 commands)]
                  :open? true}]]
    [:div.modal--container
     (if (false? shade?)
       content
       [shade-wrapper content on-close])]))

(defn notification [{:keys [title content on-close shade?] :as opts}]
  [component (assoc opts :commands [[:button.btn.btn-primary {:on-click on-close} (text :t.actions/ok)]])])

(defn guide
  []
  [:div
   (component-info component)
   (example "modal component"
            [component {:title "Hello World"
                        :content [:div "Hello world!"]
                        :commands [[:button.btn.btn-secondary "Say"] [:button.btn.btn-primary "IT"]]
                        :on-close #(.alert js/window "close")
                        :shade? false}])
   (component-info notification)
   (example "notification modal"
            [notification {:title "Hello World"
                           :content [:div "Hello world!"]
                           :on-close #(.alert js/window "close")
                           :shade? false}])])
