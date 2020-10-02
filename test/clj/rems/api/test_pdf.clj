(ns ^:integration rems.api.test-pdf
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [luminus.http-server]
            [rems.db.api-key :as api-key]
            [rems.db.testing :refer [reset-db-fixture test-db-fixture]]
            [rems.api.testing :refer [handler-fixture]]
            [rems.json :as json]
            [rems.config]
            [rems.handler :refer [handler]]
            [rems.db.test-data-helpers :as test-helpers]))

(use-fixtures
  :each
  test-db-fixture
  reset-db-fixture
  handler-fixture)

(defn- create-test-data []
  (api-key/add-api-key! 42 {:comment "test data"})
  (test-helpers/create-user! {:eppn "handler"})
  (test-helpers/create-user! {:eppn "reporter"})
  (test-helpers/create-user! {:eppn "applicant"})
  (let [wfid (test-helpers/create-workflow! {:handlers ["handler"]})
        form (test-helpers/create-form! nil)
        res-id1 (test-helpers/create-resource! nil)
        item-id1 (test-helpers/create-catalogue-item! {:form-id form :workflow-id wfid :resource-id res-id1})
        app-id (test-helpers/create-draft! "applicant" [item-id1] "draft")]
    (test-helpers/submit-application app-id "applicant")))

(deftest test-experimental-pdf-smoke
  ;; need to spin up an actual http server so that something can serve
  ;; the headless chrome that generates the pdf
  (let [port 3093] ;; no way to automatically assign port with the ring jetty adapter
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :public-url (str "http://localhost:" port "/"))]
      (let [server (luminus.http-server/start {:handler handler :port port})]
        (try
          (create-test-data)
          (let [application-id (-> (http/get (str "http://localhost:" port "/api/applications")
                                             {:headers {"x-rems-api-key" "42"
                                                        "x-rems-user-id" "handler"}})
                                   :body
                                   json/parse-string
                                   first
                                   :application/id)
                response (http/get (str "http://localhost:" port "/api/applications/" application-id "/experimental/pdf")
                                   {:throw-exceptions false
                                    :headers          {"x-rems-api-key" "42"
                                                       "x-rems-user-id" "reporter"}})]
            (is (= 200 (:status response)))
            (is (= "application/pdf" (get-in response [:headers "Content-Type"])))
            (is (.startsWith (:body response) "%PDF-")))
          (finally
            (luminus.http-server/stop server)))))))

