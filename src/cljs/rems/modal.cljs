(ns rems.modal
  (:require [reagent.core :as r]
            [react-dom :as react-dom]
            [rems.atoms :refer [close-symbol]]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text]]))

(defn modal
  "Modal component that is rendered into #modal-root element.
   
   Pass a map of options with the following keys:
   * `id` string, unique id for modal element
   * `title` string, text shown in modal header
   * `body` hiccup, element rendered in modal body
   * `footer` hiccup, element rendered in modal body (e.g. action buttons)
   * `centered` boolean, should modal render vertically centered (default: false)
   * `large` boolean, should modal render larger (default: false)"
  [{:keys [id title body footer centered large]}]
  (let [label-id (str id "-label-id")]
    (-> [:div.modal {:id id :tabIndex -1 :role "dialog" :aria-labelledby label-id :aria-hidden true}
         [:div.modal-dialog {:role "document"
                             :class [(when centered "modal-dialog-centered")
                                     (when large "modal-lg")]}
          [:div.modal-content
           [:div.modal-header
            [:h5.modal-title {:id label-id}
             title]
            [:button.close {:type "button" :data-dismiss "modal" :aria-label (text :t.actions/cancel)}
             [:span {:aria-hidden true}
              [close-symbol]]]]
           (when body
             [:div.modal-body body])
           (when footer
             [:div.modal-footer footer])]]]
        (r/as-element)
        (react-dom/createPortal (.querySelector js/document "#modal-root")))))

(defn guide []
  (fn []
    [:div
     (component-info modal)
     (example "simple modal"
              [:p [:code "rems.modal/modal"] " is rendered into modal root."]
              (let [modal-id "simple-modal"]
                [:div
                 [modal {:id modal-id
                         :title "Simple modal"}]
                 [:button.btn.btn-secondary {:type :button
                                             :data-toggle "modal"
                                             :data-target (str "#" modal-id)}
                  "Open modal"]]))
     (example "simple modal"
              [:p [:code "rems.modal/modal"] " is rendered into modal root. Modal can also be opened by calling javascript function."]
              (let [modal-id "simple-modal-js"]
                [:div
                 [modal {:id modal-id
                         :title "Simple modal"}]
                 [:button.btn.btn-secondary {:type :button
                                             :on-click #(doto (js/$ (str "#" modal-id))
                                                          (.modal "show"))}
                  "Open modal"]]))
     (example "simple modal with options"
              [:p [:code "rems.modal/modal"] " can be configured with different options."]
              (let [modal-id "simple-modal-with-options"
                    body [:p "I am modal content"]
                    footer [:<>
                            [:button.btn.btn-secondary {:type :button
                                                        :data-toggle "modal"
                                                        :data-target (str "#" modal-id)}
                             "Cancel"]
                            [:button.btn.btn-danger {:type :button}
                             "Do action"]]]
                [:div
                 [modal {:id modal-id
                         :title "Simple modal"
                         :body body
                         :footer footer
                         :centered true
                         :large true}]
                 [:button.btn.btn-secondary {:type :button
                                             :data-toggle "modal"
                                             :data-target (str "#" modal-id)}
                  "Open modal"]]))]))

