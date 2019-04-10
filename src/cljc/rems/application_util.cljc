(ns rems.application-util)

(defn accepted-licenses? [application userid]
  (every? (or (get (:application/accepted-licenses application)
                   userid)
              #{})
          (map :license/id (:application/licenses application))))

(defn form-fields-editable? [application]
  (contains? (or (:possible-commands application) ;; TODO: remove v1 api usage
                 (:application/permissions application))
             :application.command/save-draft))

(defn format-description [application]
  (if-let [ext-id (:application/external-id application)]
    (str ext-id " " (:application/description application))
    (:application/description application)))
