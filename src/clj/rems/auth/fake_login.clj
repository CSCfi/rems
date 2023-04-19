(ns rems.auth.fake-login
  (:require [compojure.core :refer [GET defroutes]]
            [hiccup.util :refer [url]]
            [rems.layout :as layout]
            [rems.auth.oidc :as oidc]
            [rems.common.util :refer [escape-element-id]]
            [rems.config :refer [env]]
            [rems.db.test-data-users :as test-data-users]
            [ring.util.response :refer [redirect]]))

(defn get-fake-user-descriptions []
  (case (:fake-authentication-data env)
    :test test-data-users/+user-descriptions+
    :demo [{:group "Users" ; no fancy descriptions
            :users (vec (for [[userid attrs] test-data-users/+demo-id-data+]
                          {:userid userid
                           :description (pr-str attrs)}))}]))

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
        user-data (merge id-data user-info)
        user (oidc/find-or-create-user! user-data)]
    (-> (redirect "/redirect")
        (assoc :session session)
        (assoc-in [:session :access-token] (str "access-token-" username))
        (assoc-in [:session :identity] user))))

(defn- user-selection [username]
  (let [url (url (login-url) {:username username})]
    [:div.user.m-3
     [:a.btn.btn-primary.text-truncate {:href url :style "width: 12rem"}
      username]]))

(defn- fake-login-screen [{session :session :as req}]
  (if-let [username (-> req :params :username)]
    (fake-login session username)
    (layout/render nil
                   {:app-content [:div.container
                                  [:div.row.w-100
                                   [:div.logo.w-100
                                    [:div.container.img]]]
                                  [:div.login.row.d-flex.flex-column.justify-content-start.align-items-center.mb-5
                                   [:h1.text-center "Development Login"]
                                   [:div.users
                                    (for [{:keys [group users]} (get-fake-user-descriptions)
                                          :let [id (escape-element-id group)]]
                                      [:div
                                       [:h2.mx-3 group]

                                       ;; XXX: this could be refactored so that
                                       ;; this isn't a special case but an attribute of
                                       ;; the group data
                                       (when (= "Special Users" group)
                                         [:div.ml-3
                                          [:p "Special users with special cases. Not for daily use!"]
                                          [:button#show-special-users.btn.btn-secondary {:data-toggle "collapse" :data-target (str "#" id)}
                                           "Show"]])

                                       [:div.collapse {:id id
                                                       :class (if (= "Special Users" group) "hide" "show")}
                                        (for [{:keys [userid description]} users]
                                          [:div.d-flex.flex-row.align-start.justify-center
                                           (user-selection userid)
                                           [:div.m-3 description]])]])]]]})))


(defn- fake-logout [{session :session}]
  (assoc (redirect "/") :session (dissoc session :identity)))

(defroutes routes
  (GET (login-url) req (fake-login-screen req))
  (GET (logout-url) req (fake-logout req)))
