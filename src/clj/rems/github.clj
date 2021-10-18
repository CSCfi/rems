(ns rems.github
  (:require [clj-http.client :as http]
            [medley.core :refer [find-first]]))

(defn fetch-releases-latest
  "Fetches the latest release from the GitHub repository.

  Returns the tag and zip file."
  [repository]
  (let [release-response (http/get (str "https://api.github.com/repos/" repository "/releases/latest")
                                   {:accept :json
                                    :as :json})
        release-body (:body release-response)
        release-tag (:tag_name release-body)
        zip-response (http/get (:zipball_url release-body)
                               {:as :stream})]
    {:tag release-tag
     :zip (zip-response :body)}))

(defn fetch-releases-latest-asset
  "Fetches an asset from the latest release from the GitHub repository.

  Returns the tag and asset contents."
  [repository asset]
  (let [release-response (http/get (str "https://api.github.com/repos/" repository "/releases/latest")
                                   {:accept :json
                                    :as :json})
        release-body (:body release-response)
        release-tag (:tag_name release-body)
        asset-definition (find-first (comp #{asset} :name) (release-body :assets))
        asset-response (http/get (:browser_download_url asset-definition)
                                 {:content-type "application/octet-stream"
                                  :as :stream})]
    {:tag release-tag
     :asset (asset-response :body)}))
