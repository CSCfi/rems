(ns rems.auth.fake-login
  (:require [compojure.core :refer [GET defroutes]]
            [hiccup.util :refer [url]]
            [rems.layout :as layout]
            [rems.auth.oidc :as oidc]
            [rems.config :refer [env]]
            [rems.db.test-data-users :as test-data-users]
            [ring.util.response :refer [redirect]]))

(defn get-fake-users []
  (-> (case (:fake-authentication-data env)
        :test test-data-users/+fake-id-data+
        :demo test-data-users/+demo-id-data+)
      keys))

(defn get-fake-id-data [username]
  (-> (case (:fake-authentication-data env)
        :test test-data-users/+fake-id-data+
        :demo test-data-users/+demo-id-data+)
      (get username)))

(defn get-fake-user-info [username]
  (-> (case (:fake-authentication-data env)
        :test test-data-users/+fake-user-info+
        :demo test-data-users/+demo-user-info+)
      (get username)))

(defn login-url []
  "/fake-login")

(defn logout-url []
  "/fake-logout")

(defn- fake-login [session username]
  (let [id-data (get-fake-id-data username)
        user-info (get-fake-user-info username)
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
                                     (->> (get-fake-users)
                                          sort
                                          distinct
                                          (map user-selection))]]]]})))


(defn- fake-logout [{session :session}]
  (assoc (redirect "/") :session (dissoc session :identity)))

(defroutes routes
  (GET (login-url) req (fake-login-screen req))
  (GET (logout-url) req (fake-logout req)))
