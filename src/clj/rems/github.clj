(ns rems.github
  (:require [clj-http.client :as http]
            [medley.core :refer [find-first]]))

(defn- fetch-latest [repository]
  (:body (http/get (str "https://api.github.com/repos/" repository "/releases/latest")
                   {:accept :json
                    :as :json})))

(defn fetch-releases-latest
  "Fetches the latest release from the GitHub repository.

  Returns the tag and zip file."
  [repository]
  (let [release-body (fetch-latest repository)
        zip-response (http/get (:zipball_url release-body)
                               {:as :stream})]
    {:tag (:tag_name release-body)
     :zip (:body zip-response)}))

(defn fetch-releases-latest-asset
  "Fetches an asset from the latest release from the GitHub repository.

  Returns the tag and asset contents."
  [repository asset]
  (let [release-body (fetch-latest repository)
        asset-definition (find-first (comp #{asset} :name) (release-body :assets))
        asset-response (http/get (:browser_download_url asset-definition)
                                 {:content-type "application/octet-stream"
                                  :as :stream})]
    {:tag (:tag_name release-body)
     :asset (:body asset-response)}))

(comment
  (:tag (fetch-releases-latest "EBISPOT/DUO"))
  (fetch-releases-latest-asset "monarch-initiative/mondo" "mondo.owl"))
