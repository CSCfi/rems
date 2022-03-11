(ns rems.auth.fake-login
  (:require [compojure.core :refer [GET defroutes]]
            [hiccup.util :refer [url]]
            [rems.layout :as layout]
            [rems.auth.oidc :as oidc]
            [rems.config :refer [env]]
            [rems.db.test-data-users :refer [+fake-idp-data+ +demo-idp-data+]]
            [ring.util.response :refer [redirect]]))

(defn get-fake-login-users []
  (case (:fake-authentication-data env)
    :test +fake-idp-data+
    :demo +demo-idp-data+))

(defn login-url []
  "/fake-login")

(defn logout-url []
  "/fake-logout")

(defn- fake-login [session username]
  (let [id-data (-> (get-fake-login-users) (get username))
        user-info (select-keys id-data [:researcher-status-by])
        user (oidc/find-or-create-user! id-data user-info)]
    (-> (redirect "/redirect")
        (assoc :session session)
        (assoc-in [:session :access-token] (str "access-token-" username))
        (assoc-in [:session :identity] user))))

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
                                    [:h1.text-center "Development Login"]
                                    [:div.users.d-flex.flex-wrap.justify-content-stretch.align-items-start
                                     (->> (keys (get-fake-login-users))
                                          (sort)
                                          (distinct)
                                          (map user-selection))]]]]})))


(defn- fake-logout [{session :session}]
  (assoc (redirect "/") :session (dissoc session :identity)))

(defroutes routes
  (GET (login-url) req (fake-login-screen req))
  (GET (logout-url) req (fake-logout req)))
