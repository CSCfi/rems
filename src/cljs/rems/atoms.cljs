(ns rems.atoms)

(defn external-link []
  [:i {:class "fa fa-external-link-alt"}])

(defn link-to [opts uri title]
  [:a (merge opts {:href uri}) title])

(defn image [opts src]
  [:img (merge opts {:src src})])

(defn sort-symbol [sort-order]
  [:i.fa {:class (case sort-order
                   :asc "fa-arrow-down"
                   :desc "fa-arrow-up")}])
