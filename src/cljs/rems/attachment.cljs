(ns rems.attachment
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [rems.atoms :as atoms]
            [rems.common.attachment-util :as attachment-util]
            [rems.config]
            [rems.collapsible :as collapsible]
            [rems.globals]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text text-format]]
            [rems.spinner :as spinner]
            [rems.util :refer [format-file-size]]))

(defn allowed-extensions-info []
  [:<>
   [:p (text-format :t.form/upload-extensions attachment-util/allowed-extensions-string)]
   [:p (text-format :t.form/attachment-max-size (format-file-size (:attachment-max-size @rems.globals/config)))]])

(defn allowed-extensions-toggle [{:keys [id]}]
  [collapsible/info-toggle-control
   {:aria-label (text-format :t.form/upload-extensions attachment-util/allowed-extensions-string)
    :collapsible-id id}])

(defn allowed-extensions-collapsible [{:keys [hide-controls? id] :as opts}]
  [:<>
   (when-not hide-controls?
     [allowed-extensions-toggle opts])
   [collapsible/minimal
    {:id id
     :class :info-collapsible
     :collapse [:div [allowed-extensions-info]]}]])

(defn upload-button [{:keys [hide-info? id label status on-upload]}]
  (let [upload-id (str id "-input")]
    [:div.upload-file
     [:input {:style {:display "none"}
              :type :file
              :id upload-id
              :name upload-id
              :accept attachment-util/allowed-extensions-string
              :on-change (fn [event]
                           (let [filecontent (aget (.. event -target -files) 0)
                                 form-data (doto (js/FormData.)
                                             (.append "file" filecontent))]
                             (set! (.. event -target -value) nil) ; empty selection to fix uploading the same file twice
                             (when on-upload
                               (on-upload {:form-data form-data
                                           :filename (.-name filecontent)
                                           :filecontent filecontent}))))}]
     [atoms/action-button
      (atoms/new-action {:id id
                         :outline? true
                         :label (or label (text :t.form/upload))
                         :on-click #(.click (.getElementById js/document upload-id))})]
     [:span.ml-2
      (case status
        :pending [spinner/small]
        :success nil ; the new attachment row appearing is confirmation enough
        :error [atoms/failure-symbol]
        nil)]
     (when-not hide-info?
       [allowed-extensions-collapsible {:id (str id "-info-collapsible")}])]))

(defn guide []
  (r/with-let [guide-state (r/atom nil)]
    [:div
     (component-info upload-button)
     (example "default use"
              (let [state (r/cursor guide-state ["attachment-upload-button"])]
                [upload-button {:id "attachment-upload-button"
                                :label (:filename @state)
                                :status (:status @state)
                                :on-upload (fn [{:keys [filename]}]
                                             (swap! state assoc :filename filename :status :pending)
                                             (js/setTimeout #(swap! state assoc :status :success)
                                                            1000))}]))
     (example "pending state"
              [upload-button {:id "pending-upload-button"
                              :status :pending}])
     (example "error state"
              [upload-button {:id "error-upload-button"
                              :status :error}])
     (example "show allowed extensions in different element"
              [:<>
               (into [:div [rems.attachment/allowed-extensions-info]]
                     (for [lang @rems.config/languages]
                       [upload-button {:id (str "attachment-upload-button-2-" (name lang))
                                       :label (str/upper-case (name lang))
                                       :hide-info? true}]))])]))
