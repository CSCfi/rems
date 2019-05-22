(ns rems.atoms
  (:require [clojure.string :as str]
            [komponentit.autosize :as autosize]
            [re-frame.core :as rf]
            [reagent.core :as reagent]
            [rems.guide-functions]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn external-link []
  [:i {:class "fa fa-external-link-alt"
       :aria-label (text :t.link/opens-in-new-window)}])

(defn file-download []
  [:i {:class "fa fa-file-download"
       :aria-label (text :t.link/download-file)}])

(defn link-to [opts uri title]
  [:a (merge opts {:href uri
                   :on-click (fn [] (rf/dispatch [:rems.spa/user-triggered-navigation]))})
   title])

(defn image [opts src]
  [:img (merge opts {:src src})])

(defn sort-symbol [sort-order]
  [:i.fa {:class (case sort-order
                   :asc "fa-arrow-up"
                   :desc "fa-arrow-down")}])

(defn search-symbol []
  [:i.fa {:class "fa-search"}])

(defn close-symbol []
  [:i.fa {:class "fa-times"}])

(defn textarea [attrs]
  [autosize/textarea (merge {:min-rows 5}
                            (update attrs :class #(str/trim (str "form-control " %))))])

(defn flash-message
  "Displays a notification (aka flash) message.

   :status   - one of the alert types from Bootstrap i.e. :success, :info, :warning or :danger
   :contents - content to show inside the notification"
  [{status :status contents :contents}]
  (when status
    [:div.alert {:class (str "alert-" (name status))} contents]))

(defn readonly-checkbox
  "Displays a checkbox."
  [checked?]
  (if checked?
    [:i.far.fa-lg.fa-check-square {:aria-label (text :t.form/checkbox-checked)}]
    [:i.far.fa-lg.fa-square {:aria-label (text :t.form/checkbox-unchecked)}]))

(defn info-field
  "A component that shows a readonly field with title and value.

  Used for e.g. displaying applicant attributes.

  Additional options:
  `:inline?` - puts the label and value on the same row
  `:box?`    - wrap the value into a field value box (default true)"
  [title value & [{:keys [inline? box?] :or {box? true} :as opts}]]
  (if inline?
    [:div.form-group.row
     [:label.col-sm-3.col-form-label title]
     [:div.col-sm-9 (if box? {:class "form-control"} {:style {:padding-left 0}}) value]]
    [:div.form-group
     [:label title]
     [:div (if box? {:class "form-control"} {:style {:padding-left 0}}) value]]))

(defn attachment-link
  "Renders link to the attachment with `id` and name `title`."
  [id title]
  [:a.btn.btn-secondary.mr-2
   {:href (str "api/licenses/attachments/" id)
    :target :_blank}
   title " " [external-link]])

(defn enrich-user [user]
  (assoc user :display (str (:name user) " (" (:email user) ")")))

(defn set-document-title! [title]
  (set! (.-title js/document)
        (str title
             (when-not (str/blank? title)
               " - ")
             (text :t.header/title))))

(defn document-title [_title]
  (let [on-update (fn [this]
                    (let [[_ title] (reagent/argv this)]
                      (set-document-title! title)))]
    (reagent/create-class
     {:component-did-mount on-update
      :component-did-update on-update
      :display-name "document-title"
      :reagent-render (fn [title]
                        [:h1 title])})))

(defn guide []
  [:div
   (component-info flash-message)
   (example "flash-message with info"
            [flash-message {:status :info
                            :contents "Hello world"}])

   (example "flash-message with error"
            [flash-message {:status :danger
                            :contents "You fail"}])
   (component-info readonly-checkbox)
   (example "readonly-checkbox unchecked"
            [readonly-checkbox false])
   (example "readonly-checkbox checked"
            [readonly-checkbox true])
   (component-info info-field)
   (example "info-field with data"
            [info-field "Name" "Bob Tester"])
   (example "info-field without box around value"
            [info-field "Name" "Bob Tester" {:box? false}])
   (example "info-field inline"
            [info-field "Name" "Bob Tester" {:inline? true}])
   (component-info attachment-link)
   (example "attachment-link"
            [attachment-link 1 "my-attachment.pdf"])])
