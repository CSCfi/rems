(ns rems.application-util)

(defn accepted-licenses? [application userid]
  (let [application-licenses (map :license/id (:application/licenses application))
        user-accepted-licenses (get (:application/accepted-licenses application) userid)]
    (cond (empty? application-licenses) true
          (empty? user-accepted-licenses) false
          :else (every? (set user-accepted-licenses) application-licenses))))

(defn form-fields-editable? [application]
  (contains? (:application/permissions application)
             :application.command/save-draft))

(defn get-member-name [attributes]
  (or (:name attributes)
      (:userid attributes)))

(defn get-applicant-name [application]
  (get-member-name (:application/applicant application)))
