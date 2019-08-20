(ns rems.test-text
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.application.events :as events]
            [rems.application.model :as model]
            [rems.config :refer [env]]
            [rems.test-locales :refer [loc-en]]
            [rems.text :refer [with-language localize-event localize-state]]))

(use-fixtures :once
  (fn [f]
    (mount/start #'rems.config/env #'rems.locales/translations)
    (f)
    (mount/stop)))

(defn- valid-localization? [str]
  (and (not (.contains str "Missing"))
       (not (.contains str "Unknown"))))

(deftest test-all-state-localizations
  (is (= (-> (:states (:applications (:t (loc-en))))
             (dissoc :unknown)
             (keys)
             (sort))
         (->> model/states
              (map name)
              (map keyword)
              (sort))))
  (with-language :en
    (fn []
      (is (not (valid-localization? (localize-state "foobar"))))
      (doseq [s model/states]
        (testing s
          (is (valid-localization? (localize-state s))))))))

(deftest test-all-event-localizations
  (let [event-types (keys events/event-schemas)]
    (is (= (-> (:events (:applications (:t (loc-en))))
               (dissoc :unknown)
               (keys)
               (sort))
           (->> event-types
                (map name)
                (map keyword)
                (sort))))
    (with-language :en
      (fn []
        (is (not (valid-localization? (localize-event "foobar"))))
        (doseq [event-type event-types]
          (testing event-type
            (is (valid-localization? (localize-event event-type)))))))))
