(ns rems.administration.workflow
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.atoms :refer [external-link info-field readonly-checkbox]]
            [rems.collapsible :as collapsible]
            [rems.common-util :refer [andstr]]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-time text text-format]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ workflow-id]]
   {:db (assoc db ::loading? true)
    ::fetch-workflow [workflow-id]}))

(defn- fetch-workflow [workflow-id]
  (fetch (str "/api/workflows/" workflow-id)
         {:handler #(rf/dispatch [::fetch-workflow-result %])}))

(rf/reg-fx ::fetch-workflow (fn [[workflow-id]] (fetch-workflow workflow-id)))

(rf/reg-event-db
 ::fetch-workflow-result
 (fn [db [_ workflow]]
   (-> db
       (assoc ::workflow workflow)
       (dissoc ::loading?))))

(rf/reg-sub ::workflow (fn [db _] (::workflow db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- back-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration/workflows")}
   (text :t.administration/back)])

(defn- to-create-workflow []
  [:a.btn.btn-primary
   {:href "/#/administration/create-workflow"}
   (text :t.administration/create-workflow)])

(defn inline-info-field [text value]
  [info-field text value {:inline? true}])

(defn get-localized-value [field key language]
  (key (first (filter (comp #{(name language)} :langcode)
                      (:localizations field)))))

(defn license-view [license language]
  (into [:div.form-item
         [:h4 (text-format :t.administration/license-field (get-localized-value license :title language))]
         [inline-info-field (text :t.administration/title) (:title license)]]
        (concat (for [localization (:localizations license)]
                  [inline-info-field (str (text :t.administration/title)
                                          " "
                                          (str/upper-case (:langcode localization))) (:title localization)])
                [[inline-info-field (text :t.administration/type) (:type license)]
                 (when (:textcontent license)
                   (case (:type license)
                     "link" [inline-info-field (text :t.create-license/external-link) [:a {:target :_blank :href (:textcontent license)} (:textcontent license) " " [external-link]]]
                     "text" [inline-info-field (text :t.create-license/license-text) (:textcontent license)]
                     nil))]
                (when (= "link" (:type license))
                  (for [localization (:localizations license)]
                    [inline-info-field (str (text :t.create-license/external-link)
                                            " "
                                            (str/upper-case (:langcode localization))) [:a {:target :_blank :href (:textcontent localization)} (:textcontent localization) " " [external-link]]]))
                [[inline-info-field (text :t.administration/start) (localize-time (:start license))]
                 [inline-info-field (text :t.administration/end) (localize-time (:end license))]])
        ))

(defn round-view [actors]
  [:div.form-item
   [:h4 (text-format :t.create-workflow/round-n (inc (:round (first actors))))]
   (let [{approvers "approver" reviewers "reviewer"} (group-by :role actors)]
     [:div
      (when (seq approvers)
        [inline-info-field (text :t.create-workflow/approvers) (str/join ", " (map :actoruserid approvers))])
      (when (seq reviewers)
        [inline-info-field (text :t.create-workflow/reviewers) (str/join ", " (map :actoruserid reviewers))])])])

(defn rounds-view [actors language]
  (let [rounds (vals (group-by :round actors))]
    (when (seq rounds)
      [collapsible/component
       {:id "rounds"
        :title (text :t.administration/rounds)
        :top-less-button? (> (count rounds) 5)
        :open? (<= (count rounds 3))
        :collapse
        (into [:div]
              (for [round rounds]
                [round-view round]))}])))

(defn licenses-view [licenses language]
  [collapsible/component
   {:id "licenses"
    :title (text :t.administration/licenses)
    :top-less-button? (> (count licenses) 5)
    :open? (<= (count licenses 3))
    (if (seq licenses)
      :collapse
      :always)
    (if (seq licenses)
      (into [:div]
            (for [license licenses]
              [license-view license language]))
      [:p (text :t.administration/no-licenses)])}])

(defn workflow-view [workflow language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "workflow"
     :title [:span (andstr (:organization workflow) "/") (:title workflow)]
     :always [:div
              [inline-info-field (text :t.administration/organization) (:organization workflow)]
              [inline-info-field (text :t.administration/title) (:title workflow)]
              [inline-info-field (text :t.administration/type)
               (cond (:workflow workflow) (text :t.create-workflow/dynamic-workflow)
                     (seq (:actors workflow)) (text :t.create-workflow/rounds-workflow)
                     :else (text :t.create-workflow/auto-approve-workflow))]
              (when (:workflow workflow)
                [inline-info-field (text :t.create-workflow/handlers) (str/join ", " (get-in workflow [:workflow :handlers]))])
              [inline-info-field (text :t.administration/start) (localize-time (:start workflow))]
              [inline-info-field (text :t.administration/end) (localize-time (:end workflow))]
              [inline-info-field (text :t.administration/active) [readonly-checkbox (:active workflow)]]]}]
   [rounds-view (:actors workflow) language]
   [licenses-view (:licenses workflow) language]
   [:div.col.commands [back-button]]])

(defn workflow-page []
  (let [workflow (rf/subscribe [::workflow])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration-navigator-container]
       [:h2 (text :t.administration/workflow)]
       (if @loading?
         [spinner/big]
         [workflow-view @workflow @language])])))
