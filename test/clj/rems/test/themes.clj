(ns rems.test.themes
  (:require [clojure.test :refer :all]
            [rems.themes :as themes]
            [rems.util :as util]
            [mount.core :as mount]))

(use-fixtures :once (fn [f]
                      (f)
                      (mount/stop)))

(deftest load-theme-test
  (let [default-theme {:default "foo"}]
    (testing "no theme gives default theme"
      (is (= default-theme
             (themes/load-theme nil default-theme))))
    (testing "non-existing theme gives default theme"
      (is (= default-theme
             (themes/load-theme "no-such-file.edn" default-theme))))
    (testing "static resources can be placed in 'public' directory next to the theme file"
      (is (= "lbr-theme/public"
             (:static-resources-path (themes/load-theme "lbr-theme/lbr.edn" default-theme)))))
    (testing "overrides values from the default theme"
      (is (= {:color1 "#CAD2E6"
              :color42 "not overridden"}
             (select-keys (themes/load-theme "lbr-theme/lbr.edn"
                                             {:color1 "should be overridden"
                                              :color42 "not overridden"})
                          [:color1 :color42]))))))

(deftest get-theme-attribute-test
  (-> (mount/only [#'rems.themes/theme])
      (mount/swap {#'rems.themes/theme {:test "success"
                                        :test-color 2}})
      (mount/start))
  (is (= 2 (util/get-theme-attribute :test-color)))
  (is (= "success" (util/get-theme-attribute :test)))
  (is (nil? (util/get-theme-attribute :no-such-attribute))))
