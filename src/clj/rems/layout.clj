(ns rems.layout
  (:require [hiccup.page :refer [html5 include-css include-js]]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.users :as users]
            [rems.json :as json]
            [rems.text :refer [text]]
            [rems.util :refer [get-user-id]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [ring.util.http-response :as response]))

(defn initialize-hooks []
  [:script {:type "text/javascript"}
   "
window.rems = {
  hooks: {
    get: function () {},
    put: function () {},
    navigate: function () {}
  }
};
"])

(defn- css-filename [language]
  (str "/css/" (name language) "/screen.css"))

(defn- page-template
  [content]
  (html5 [:html {:lang "en"}
          [:head
           [:meta {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]
           [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
           [:link {:rel "icon" :href "/img/favicon.ico" :type "image/x-icon"}]
           [:link {:rel "shortcut icon" :href "/img/favicon.ico" :type "image/x-icon"}]
           [:title (text :t.header/title)]
           (include-css "/assets/bootstrap/css/bootstrap.min.css")
           (include-css "/assets/font-awesome/css/all.css")
           (include-css (css-filename (@env :default-language)))]
          [:body
           [:div#app]
           (include-js "/assets/font-awesome/js/fontawesome.js")
           (include-js "/assets/better-dom/dist/better-dom.js")
           (include-js "/assets/better-dateinput-polyfill/dist/better-dateinput-polyfill.js")
           (include-js "/assets/jquery/jquery.min.js")
           (include-js "/assets/popper.js/dist/umd/popper.min.js")
           ;; XXX: diff-match-patch is an NPM module and not meant to be included with a script tag
           [:script {:type "text/javascript"} "module = {};"]
           (include-js "/assets/diff-match-patch/index.js")
           [:script {:type "text/javascript"} "delete module;"]
           (include-js "/assets/tether/dist/js/tether.min.js")
           (include-js "/assets/bootstrap/js/bootstrap.min.js")
           (initialize-hooks)
           (for [extra-script (get-in @env [:extra-scripts :files])]
             (include-js extra-script))
           content]]))

(defn render
  "renders HTML generated by Hiccup

   params: :status -- status code to return, defaults to 200
           :headers -- map of headers to return, optional
           :content-type -- optional, defaults to \"text/html; charset=utf-8\""
  [page-name content & [params]]
  (let [content-type (:content-type params "text/html; charset=utf-8")
        status (:status params 200)
        headers (:headers params {})]
    (response/content-type
     {:status status
      :headers headers
      :body (page-template content)}
     content-type)))

(defn home-page []
  (users/add-user-if-logged-in! (get-user-id) context/*user*)
  (render
   "REMS"
   (list
    [:script {:type "text/javascript"}
     (format "var csrfToken = '%s';" *anti-forgery-token*)]
    (include-js "/js/app.js")
    [:script {:type "text/javascript"}
     (format "rems.app.setIdentity(%s);" (json/generate-string {:user context/*user* :roles context/*roles*}))])))

(defn- error-content
  [error-details]
  [:div.container-fluid
   [:div.row-fluid
    [:div.col-lg-12
     [:div.centering.text-center
      [:div.text-center
       [:h1
        [:span.text-danger (str "Error: " (error-details :status))]
        [:hr]
        (when-let [title (error-details :title)]
          [:h2.without-margin title])
        (when-let [message (error-details :message)]
          [:h4.text-danger message])]]]]]])

(defn error-page
  "error-details should be a map containing the following keys:
   :status - error status
   :title - error title (optional)
   :message - detailed error message (optional)

   returns a response map with the error page as the body
   and the status specified by the status key"
  [error-details]
  (render "error page" (error-content error-details) error-details))
