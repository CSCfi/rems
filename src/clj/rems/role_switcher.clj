(ns rems.role-switcher
  (:require [compojure.core :refer [POST defroutes]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.context :as context]
            [rems.db.roles :as roles]
            [rems.guide :refer :all]
            [rems.text :refer :all]
            [rems.util :refer [get-user-id]]
            [ring.util.response :refer [redirect]]))

(defn- localize-role
  [role]
  (case role
    :applicant :t.roles.names/applicant
    :reviewer :t.roles.names/reviewer
    :approver :t.roles.names/approver
    :t.roles.names/unknown))

(defroutes role-switcher-routes
  (POST "/active_role/:role" {{role :role} :params
                              {referer "referer"} :headers}
        (roles/set-active-role! (get-user-id) (keyword role))
        (redirect referer :see-other)))

(defn role-switcher
  "Role switcher widget"
  []
  (when (< 1 (count context/*roles*))
    [:div.role-switcher
     [:span
      (text :t.roles/header)]
     (doall
      (for [role (sort context/*roles*)]
        [:form.inline {:method "post" :action (str "/active_role/" (name role))}
         (anti-forgery-field)
         [:button {:class (if (= role context/*active-role*)
                            "btn-link active"
                            "btn-link")
                   :type "submit"}
          (text (localize-role role))]]))]))

(defn has-roles?
  "Checks that the `context/*active-role*` matches one of the given roles."
  [& roles]
  (contains? (set roles) context/*active-role*))

(defmacro when-roles
  "Executes the body when the active role is one of the given roles."
  [roles & body]
  `(when (has-roles? ~@roles)
     ~@body))

(defmacro when-role
  "Executes the body when the active role is the given role."
  [role & body]
  `(when-roles #{~role} ~@body))

(defn guide []
  (list
   (example "switcher with one role"
            (binding [context/*roles* #{:approver}
                      context/*active-role* :approver]
              (role-switcher)))
   (example "switcher with multiple roles"
            (binding [context/*roles* #{:applicant :reviewer :approver}
                      context/*active-role* :reviewer]
              (role-switcher)))))
