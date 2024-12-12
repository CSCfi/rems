(ns rems.attachment
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [rems.atoms :as atoms]
            [rems.collapsible :as collapsible]
            [rems.common.attachment-util :as attachment-util]
            [rems.config]
            [rems.flash-message :as flash-message]
            [rems.globals]
            [rems.guide-util :refer [component-info example]]
            [rems.spinner :as spinner]
            [rems.text :refer [text text-format]]
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

(defn upload-error-handler [location description & [{:keys [file-name file-size]}]]
  (fn [response]
    (cond (= 413 (:status response))
          (flash-message/show-default-error! location description
                                             [:div
                                              [:p [text :t.form/too-large-attachment]]
                                              [:p (str file-name " " (format-file-size file-size))]
                                              [:p [text-format :t.form/attachment-max-size (format-file-size (:attachment-max-size @rems.globals/config))]]])

          (= 415 (:status response))
          (flash-message/show-default-error! location description
                                             [:div
                                              [:p [text :t.form/invalid-attachment]]
                                              [:p [text-format :t.form/upload-extensions attachment-util/allowed-extensions-string]]])

          :else ((flash-message/default-error-handler location description) response))))

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
      (cond-> {:id id
               :outline? true
               :label (or label (text :t.form/upload))
               :on-click #(.click (.getElementById js/document upload-id))}
        (or (= :error status) (nil? status))
        atoms/new-action

        (= :pending status)
        (update :label (fn [s]
                         [:span.d-flex.align-items-center.gap-1 s [spinner/small]])))]

     (when (= :error status)
       [:span.ml-2
        [atoms/failure-symbol]])

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
