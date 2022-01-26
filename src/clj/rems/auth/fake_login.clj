(ns rems.auth.fake-login
  (:require [compojure.core :refer [GET defroutes]]
            [hiccup.util :refer [url]]
            [rems.layout :as layout]
            [rems.auth.oidc :as oidc]
            [rems.config :refer [env]]
            [rems.db.test-data-users :refer [+fake-user-data+ +demo-user-data+]]
            [rems.common.util :refer [assoc-some-in]]
            [rems.ga4gh :as ga4gh]
            [ring.util.response :refer [redirect]]))

(defn get-fake-login-users []
  (case (:fake-authentication-data env)
    :test +fake-user-data+
    :demo +demo-user-data+))

(defn get-researcher-status [id-data]
  (or (oidc/get-researcher-status id-data)
      (select-keys id-data [:researcher-status-by])))

(defn login-url []
  "/fake-login")

(defn logout-url []
  "/fake-logout")

(defn- fake-login [session username]
  (let [users (get-fake-login-users)
        id-data (get users username)]
    (when (oidc/should-map-userid? id-data) (oidc/create-user-mapping! id-data))
    (-> (redirect "/redirect")
        (assoc :session session)
        (assoc-in [:session :access-token] (str "access-token-" username))
        (assoc-in [:session :identity] (merge (oidc/get-user-attributes id-data)
                                              (get-researcher-status id-data))))))

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
