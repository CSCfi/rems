(ns rems.migrations.convert-to-dynamic-applications
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [rems.db.core :as db :refer [*db*]]
            [rems.db.workflow :as workflow]))
