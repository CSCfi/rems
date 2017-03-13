(ns rems.contents
  (:require [hiccup.element :refer [link-to image]]
            [rems.cart :as cart]
            [rems.form :as form]
            [rems.text :refer :all]
            [rems.db.core :as db]
            [rems.context :as context]))

(defn login [context]
  [:div.jumbotron
   [:h2 (text :t.login/title)]
   [:p (text :t.login/text)]
   (link-to (str context "/Shibboleth.sso/Login") (image {:class "login-btn"} "/img/haka_landscape_large.gif"))])

(defn about []
  [:p (text :t.about/text)])

;; TODO duplication between cart and catalogue to be factored out

(defn get-catalogue-item-title [item]
  (let [localized-title (get-in item [:localizations context/*lang* :title])]
    (or localized-title (:title item))))

(defn cart-item [item]
  [:tr
   [:td {:data-th ""} (get-catalogue-item-title item)]
   [:td.actions {:data-th ""}
    (form/link-to-form item)
    (cart/remove-from-cart-button item)]])

(defn cart-list [items]
  (when-not (empty? items)
    [:div.outer-cart
     [:div.inner-cart
      [:div.cart-title
       [:i.fa.fa-shopping-cart]
       [:span (text :t.cart/header)]]
      [:table.rems-table.cart
       (for [item (sort-by get-catalogue-item-title items)]
         (cart-item item))]]]))

(defn urn-catalogue-item? [{:keys [resid]}]
  (and resid (.startsWith resid "http://urn.fi")))

(defn catalogue-item [item]
  (let [resid (:resid item)
        title (get-catalogue-item-title item)
        component (if (urn-catalogue-item? item)
                    [:a.catalogue-item-link {:href resid :target :_blank} title]
                    title)]
    [:tr
     [:td {:data-th (text :t.catalogue/header)} component]
     [:td {:data-th ""} (cart/add-to-cart-button item)]]))

(defn catalogue-list [items]
  [:table.rems-table
   [:tr
    [:th (text :t.catalogue/header)]
    [:th ""]]
   (for [item (sort-by get-catalogue-item-title items)]
     (catalogue-item item))])

(defn catalogue []
  (list
   (cart-list (cart/get-cart-items))
   (catalogue-list (db/get-localized-catalogue-items))))

(defn form [id]
  (let [form (db/get-form-for-catalogue-item
              {:id (Long/parseLong id) :lang (name context/*lang*)})
        items (db/get-form-items {:id (:formid form)})]
    (form/form form items)))
