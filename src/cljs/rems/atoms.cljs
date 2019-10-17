(ns rems.atoms
  (:require [clojure.string :as str]
            [komponentit.autosize :as autosize]
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

(defn link [opts uri title]
  [:a (merge opts {:href uri})
   title])

(defn image [opts src]
  [:img (merge opts {:src src})])

(defn sort-symbol [sort-order]
  (let [[class label] (case sort-order
                        :asc ["fa-arrow-up" :t.table/ascending-order]
                        :desc ["fa-arrow-down" :t.table/descending-order])]
    [:i.fa {:class class
            :aria-label (text label)}]))

(defn search-symbol []
  [:i.fa {:class "fa-search"}])

(defn close-symbol []
  [:i.fa {:class "fa-times"}])

(defn success-symbol []
  ;; fa-stack has weird spacing, try to fix it by unsetting line-height (which is 2em by default)
  [:span.fa-stack {:aria-label (text :t.form/success) :style {:line-height :inherit}}
   [:i {:class "fas fa-circle fa-stack-1x icon-stack-background"}]
   [:i {:class "fas fa-check-circle fa-stack-1x text-success"}]])

(defn empty-symbol []
  [:i.fa-stack])

(defn textarea [attrs]
  [autosize/textarea (merge {:min-rows 5}
                            (update attrs :class #(str/trim (str "form-control " %))))])

(defn flash-message
  "Displays a notification (aka flash) message.

   :id - HTML element ID
   :status   - one of the alert types from Bootstrap i.e. :success, :info, :warning or :danger
   :contents - content to show inside the notification"
  [{:keys [id status contents]}]
  (when status
    [:div.flash-message.alert {:class (str "alert-" (name status))
                               :id id}
     contents]))

(defn checkbox
  "Displays a checkbox."
  [checked? on-change]
  (let [wrapped-on-change (fn [e]
                            (.preventDefault e)
                            (.stopPropagation e)
                            (when on-change
                              (on-change checked?)))]
    [:i.far.fa-lg {:class [(if checked? :fa-check-square :fa-square) (when-not on-change :readonly-checkbox)]
                   :tabIndex 0
                   :role :checkbox
                   :aria-checked checked?
                   :aria-label (if checked? (text :t.form/checkbox-checked) (text :t.form/checkbox-unchecked))
                   :on-click wrapped-on-change
                   :on-key-press #(when (= (.-key %) " ")
                                    (wrapped-on-change %))}]))

(defn readonly-checkbox
  "Displays a readonly checkbox."
  [checked?]
  [checkbox checked? nil])

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
   {:href (str "/api/licenses/attachments/" id)
    :target :_blank}
   title " " [external-link]])

(defn enrich-user [user]
  (assoc user :display (str (or (:name user)
                                (:userid user))
                            (when (:email user)
                              (str " (" (:email user) ")")))))

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
  (let [state (reagent/atom false)
        on-change #(swap! state not)]
    (fn []
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
       (example "checkbox interactive unchecked"
                [checkbox @state on-change])
       (component-info info-field)
       (example "info-field with data"
                [info-field "Name" "Bob Tester"])
       (example "info-field without box around value"
                [info-field "Name" "Bob Tester" {:box? false}])
       (example "info-field inline"
                [info-field "Name" "Bob Tester" {:inline? true}])
       (component-info attachment-link)
       (example "attachment-link"
                [attachment-link 1 "my-attachment.pdf"])])))
