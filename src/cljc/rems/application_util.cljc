(ns rems.application-util)

(def ^:private applicant-name-order [:commonName :displayName :eppn])

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

(defn get-applicant-name [application]
  (let [attributes (:application/applicant-attributes application)]
    (when attributes
      (first (filter identity (map attributes applicant-name-order))))))
