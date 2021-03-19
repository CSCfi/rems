(ns rems.common.catalogue-util
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- urn? [resid]
  (and resid (str/starts-with? resid "urn:")))

(defn- urn-catalogue-item-url [resid {:keys [urn-organization]}]
  (when (urn? resid)
    (str (or urn-organization "http://urn.fi/") resid)))

;; EGA catalogue items (i.e. datasets) look like EGAD00001006673 and could be linked to e.g. https://ega-archive.org/datasets/EGAD00001006673

(defn- ega-dataset? [resid]
  (and resid (str/starts-with? resid "EGAD")))

(defn- ega-catalogue-item-url [resid {:keys [enable-ega ega-organization]}]
  (when (and enable-ega (ega-dataset? resid))
    (str (or ega-organization "https://ega-archive.org/datasets/") resid)))

;; Resource can have different schemas here (V2Resource vs. CatalogueItem)
(defn catalogue-item-more-info-url [resource-or-item language config]
  (let [default-language (:default-language config)]
    (or (get-in resource-or-item [:catalogue-item/infourl language])
        (get-in resource-or-item [:catalogue-item/infourl default-language])
        (get-in resource-or-item [:localizations language :infourl])
        (get-in resource-or-item [:localizations default-language :infourl])
        (urn-catalogue-item-url (:resource/ext-id resource-or-item) config)
        (urn-catalogue-item-url (:resid resource-or-item) config)
        (ega-catalogue-item-url (:resource/ext-id resource-or-item) config)
        (ega-catalogue-item-url (:resid resource-or-item) config))))

(deftest test-catalogue-item-more-info-url
  (testing "basic infourls"
    (is (= nil (catalogue-item-more-info-url nil :en nil)))
    (is (= "http://item.fi" (catalogue-item-more-info-url {:catalogue-item/infourl {:fi "http://item.fi" :en "http://item.en"}} :fi {:default-language :en}))
        "basic catalogue item")
    (is (= "http://item.en" (catalogue-item-more-info-url {:catalogue-item/infourl {:en "http://item.en"}} :fi {:default-language :en}))
        "falls back to default language for catalogue item")
    (is (= "http://item.fi" (catalogue-item-more-info-url {:localizations {:fi {:infourl "http://item.fi"} :en {:infourl "http://item.en"}}} :fi {:default-language :en}))
        "basic resource")
    (is (= "http://item.en" (catalogue-item-more-info-url {:localizations {:en {:infourl "http://item.en"}}} :fi {:default-language :en}))
        "falls back to default language for resource"))

  (testing "URN"
    (is (= "http://urn.fi/urn:nbn:fi:lb-201403262" (catalogue-item-more-info-url {:resource/ext-id "urn:nbn:fi:lb-201403262"} nil nil))
        "URN works for catalogue item")
    (is (= "http://urn.fi/urn:nbn:fi:lb-201403262" (catalogue-item-more-info-url {:resid "urn:nbn:fi:lb-201403262"} nil nil))
        "URN works for resource")
    (is (= "https://urn.org/urn:nbn:fi:lb-201403262" (catalogue-item-more-info-url {:resid "urn:nbn:fi:lb-201403262"} nil {:urn-organization "https://urn.org/"}))
        "setting custom URN organization works"))

  (testing "EGA"
    (is (= nil (catalogue-item-more-info-url {:resource/ext-id "EGAD00001006673"} nil nil))
        "EGA without feature flag should not match")
    (is (= "https://ega-archive.org/datasets/EGAD00001006673" (catalogue-item-more-info-url {:resource/ext-id "EGAD00001006673"} nil {:enable-ega true}))
        "EGA works for catalogue item")
    (is (= "https://ega-archive.org/datasets/EGAD00001006673" (catalogue-item-more-info-url {:resid "EGAD00001006673"} nil {:enable-ega true}))
        "EGA works for resource")
    (is (= "https:/ega.org/EGAD00001006673" (catalogue-item-more-info-url {:resid "EGAD00001006673"} nil {:enable-ega true
                                                                                                          :ega-organization "https:/ega.org/"}))
        "setting custom EGA organization works"))

  (testing "overrides"
    (is (= "http://item.fi"
           (catalogue-item-more-info-url {:localizations {:fi {:infourl "http://item.fi"} :en {:infourl "http://item.en"}}
                                          :resid "EGAD00001006673"}
                                         :fi
                                         {:enable-ega true})
           (catalogue-item-more-info-url {:catalogue-item/infourl {:fi "http://item.fi" :en "http://item.en"}
                                          :resid "EGAD00001006673"}
                                         :fi
                                         {:enable-ega true}))
        "resource or item specific infourl overrides default")))
