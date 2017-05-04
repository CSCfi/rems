(ns rems.collapsible)

(defn header
  [href expanded aria-controls title]
  [:h3.card-header
   [:a.card-title (merge {:data-toggle "collapse" :data-parent "#accordion" :href href :aria-expanded expanded :aria-controls aria-controls}
                         (when-not expanded {:class "collapsed"}))
    title]])
