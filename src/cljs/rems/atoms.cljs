(ns rems.atoms)

(defn external-link []
  [:i {:class "fa fa-external-link"}])

(defn link-to [opts uri title]
  [:a (merge opts {:href uri}) title])

(defn image [opts src]
  [:img (merge opts {:src src})])
