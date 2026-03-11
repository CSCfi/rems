(ns rems.connection)

(rf/reg-sub ::server-connection (fn [db _] (::server-connection db)))
(rf/reg-event-db ::set-server-connection (fn [db [_ x]] (assoc db ::server-connection x)))

(rf/reg-event-fx
 ::long-poll
 (fn [{:keys [db]} [_ client-id application-id]]
   (fetch (str "/api/applications/" application-id "/long-poll?client-id=" client-id)
          {:handler (fn [result]
                      (rf/dispatch [::set-server-connection result])
                      (when-some [full-reload (:full-reload result)]
                        (reload! application-id full-reload))
                      (when-some [field-values (seq (:field-values result))]
                        (rf/dispatch [::update-edit-application field-values]))
                      (when-some [attachments (:application/attachments result)]
                        (rf/dispatch [::update-application-attachments attachments]))
                      (.setTimeout js/window #(rf/dispatch [::long-poll client-id application-id]) 100))
           :error-handler #(rf/dispatch [::set-server-connection {:status :error
                                                                  :error %}])})
   {:db (assoc-in db [::server-connection :status] :fetching)}))
