(ns ^:integration rems.db.test-licenses
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.cache :as cache]
            [rems.db.licenses]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-license-cache
  (testing "cache reload works"
    (let [org-id (test-helpers/create-organization! {})
          license-id (test-helpers/create-license! {:license/type :link
                                                    :organization {:organization/id org-id}
                                                    :license/title {:en "Test License"
                                                                    :fi "Testilisenssi"
                                                                    :sv "Testlicens"}
                                                    :license/link {:en "http://example.com/license/en"
                                                                   :fi "http://example.com/license/fi"
                                                                   :sv "http://example.com/license/sv"}})]
      ;; force cache reload
      (cache/set-uninitialized! rems.db.licenses/license-cache)
      (cache/set-uninitialized! rems.db.licenses/license-localizations-cache)

      ;; verify license is cached correctly
      (is (= {license-id {:id license-id
                          :licensetype "link"
                          :organization org-id
                          :enabled true
                          :archived false}}
             (into {} (cache/entries! rems.db.licenses/license-cache))))

      (testing "localizations cache"
        (is (= {license-id {:en {:licid license-id
                                 :langcode :en
                                 :title "Test License"
                                 :textcontent "http://example.com/license/en"}
                            :fi {:licid license-id
                                 :langcode :fi
                                 :title "Testilisenssi"
                                 :textcontent "http://example.com/license/fi"}
                            :sv {:licid license-id
                                 :langcode :sv
                                 :title "Testlicens"
                                 :textcontent "http://example.com/license/sv"}}}
               (into {} (cache/entries! rems.db.licenses/license-localizations-cache))))))))