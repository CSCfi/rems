(ns rems.form
  (:require [hiccup.form :as f]))

(defn text-field [{title :title order :itemorder prompt :inputprompt}]
  (let [nam (str "text" order)]
    [:div.form-group
     [:label {:for nam} title]
     [:input.form-control {:type "text" :id nam :value prompt}]]))

(defn- field [f]
  (case (:type f)
    "text" (text-field f)
    "texta" (text-field f)
    [:p "Unsupported field " (pr-str f)]))

(defn form [form fields]
  [:form
   [:h3 (or (:formtitle form) (:metatitle form))]
   (for [f fields]
     (field f))])

(defn link-to-form [item]
  [:a.btn.btn-primary {:href (str "/form/" (:id item))} "Apply"])
