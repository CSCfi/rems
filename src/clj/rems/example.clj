(ns rems.example)

(defn example [name content]
  [:div.example
   [:h3 name]
   [:div.example-content content
    [:div.example-content-end]]])
