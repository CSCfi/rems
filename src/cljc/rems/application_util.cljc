(ns rems.application-util)

(defn accepted-licenses? [application userid]
  (let [application-licenses (map :license/id (:application/licenses application))
        user-accepted-licenses (get (:application/accepted-licenses application) userid)]
    (cond (empty? application-licenses) true
          (empty? user-accepted-licenses) false
          :else (every? (set user-accepted-licenses) application-licenses))))

(defn form-fields-editable? [application]
  (contains? (or (:possible-commands application) ;; TODO: remove v1 api usage
                 (:application/permissions application))
             :application.command/save-draft))

(def ^:private name-attribute-priority [:name :commonName :displayName :eppn])

(defn get-member-name [attributes]
  (when attributes
    (->> (map attributes name-attribute-priority)
         (remove nil?)
         first)))

(defn get-applicant-name [application]
  (get-member-name (:application/applicant-attributes application)))
