(ns rems.attachment
  (:require [rems.atoms :as atoms]
            [rems.common.attachment-util :as attachment-util]
            [rems.common.util :refer [not-blank]]
            [rems.collapsible :as collapsible]
            [rems.globals]
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

(defn upload-button [{:keys [filename hide-info? id status on-upload]}]
  (let [upload-id (str id "-input")
        info-collapsible-id (str id "-info-collapsible")]
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
                                           :filecontent filecontent}))))}]
     [atoms/action-button
      (atoms/new-action {:id id
                         :outline? true
                         :label (or (not-blank filename) ; can show filename while upload is in progress
                                    (text :t.form/upload))
                         :on-click #(.click (.getElementById js/document upload-id))})]
     [:span.ml-2
      (case status
        :pending [spinner/small]
        :success nil ; the new attachment row appearing is confirmation enough
        :error [atoms/failure-symbol]
        nil)]
     (when-not hide-info?
       [allowed-extensions-collapsible {:id info-collapsible-id}])]))
