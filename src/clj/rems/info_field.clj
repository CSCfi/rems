(ns rems.info-field)

(defn component [title value]
  [:div.form-group
   [:label title]
   [:input.form-control {:type "text" :value value :readonly true}]])
