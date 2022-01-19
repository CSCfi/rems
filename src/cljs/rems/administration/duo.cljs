(ns rems.administration.duo
  (:require [clojure.string :as str]
            [rems.administration.components :refer [inline-info-field]]
            [rems.collapsible :as collapsible]
            [rems.text :refer [localized text text-format]]
            [rems.fields :refer [info-collapse]]
            [rems.common.duo :refer [duo-restriction-label]]))

(defn- duo-restriction [{:keys [type values]}]
  (case type
    :mondo [:div.container-fluid.pt-2
            [:label (text (get duo-restriction-label type))]
            (into [:div.solid-group]
                  (for [mondo values]
                    ^{:key mondo}
                    [inline-info-field
                     (:id mondo) (:label mondo)]))]

    [inline-info-field
     (text (get duo-restriction-label type)) (-> values first :value)]))

(defn- duo-view-compact [duo]
  [:div.form-item
   [:h3.license-title (str (:shorthand duo) " – " (localized (:label duo)))]
   [inline-info-field
    (text :t.administration.duo/code) (:id duo)]
   [inline-info-field
    (text :t.administration.duo/description) (localized (:description duo))]
   (when-let [restrictions (->> (:restrictions duo) (filter (comp seq :values)) not-empty)]
     (for [restriction restrictions]
       ^{:key type}
       [duo-restriction restriction]))])

(defn resource-duo-view-compact
  "Same as duo-view-compact, but collapses restrictions by default and uses less prominent header."
  [duo resid]
  (let [duo-info-id (let [duoid (str/replace (:id duo) ":" "-")]
                      (str "res-" resid "-" duoid))]
    [:div.pt-2 {:style {:word-break :break-word}}
     [:div
      (str (:shorthand duo) " – " (localized (:label duo)))
      [info-collapse
       {:info-id duo-info-id
        :aria-label-text (text-format :t.administration.duo/about-duo-code)
        :content [:div.form-item.solid-group
                  [inline-info-field
                   (text :t.administration.duo/code) (:id duo)]
                  [inline-info-field
                   (text :t.administration.duo/description) (localized (:description duo))]
                  (when-let [restrictions (->> (:restrictions duo) (filter (comp seq :values)) not-empty)]
                    (for [restriction restrictions]
                      ^{:key type}
                      [duo-restriction restriction]))]}]]]))

(defn duos-view [duos]
  [collapsible/component
   {:id "duos"
    :title (text :t.administration.duo/codes)
    :top-less-button? false
    :open? true
    :collapse (if (seq duos)
                (into [:div]
                      (for [duo duos]
                        [duo-view-compact duo]))
                [:p (text :t.administration/no-duo-codes)])}])
