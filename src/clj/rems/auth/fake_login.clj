(ns rems.auth.fake-login
  (:require [clojure.string :as str]
            [compojure.core :refer [GET defroutes]]
            [hiccup.util :refer [url]]
            [rems.layout :as layout]
            [rems.db.core :as db]
            [rems.db.users :as users]
            [ring.util.response :refer [redirect]]))

(defn login-url []
  "/fake-login")

(defn logout-url []
  "/fake-logout")

(defn- fake-login [session username]
  (assoc (redirect "/redirect")
         :session (assoc session :identity (users/get-raw-user-attributes username))))

(defn- user-selection [username]
  (let [url (url (login-url) {:username username})]
    [:div.user.mb-3.mr-3.flex-fill.d-flex {:onclick (str "window.location.href='" url "';")}
     [:a.btn.btn-primary.flex-fill {:href url} username]]))

(defn- fake-login-screen [{session :session :as req}]
  (if-let [username (-> req :params :username)]
    (fake-login session username)
    (layout/render nil
                   {:app-content [:div.d-flex.justify-content-start.align-items-center
                                  [:div.row.w-100
                                   [:div.logo.w-100
                                    [:div.container.img]]]
                                  [:div.login.row.d-flex.justify-content-center.align-items-center
                                   [:div.col-md-8
                                    [:h1 {:tabIndex 0
                                          :class "text-center"}
                                      "Development Login"]
                                    [:div.users.d-flex.flex-wrap.justify-content-stretch.align-items-start
                                     (->> (map :userid (db/get-users))
                                          (remove #(str/starts-with? % "perftester"))
                                          (remove #(str/ends-with? % "-bot"))
                                          (sort)
                                          (distinct)
                                          (map user-selection))]]]]})))


(defn- fake-logout [{session :session}]
  (assoc (redirect "/") :session (dissoc session :identity)))

(defroutes routes
  (GET (login-url) req (fake-login-screen req))
  (GET (logout-url) req (fake-logout req)))
