(ns rems.cljs-tests
  (:require [doo.runner :refer-macros [doo-tests]]
            [rems.test.administration.license]
            [rems.test.administration.workflow]
            [rems.test.table]))

(doo-tests 'rems.test.administration.license
           'rems.test.administration.workflow
           'rems.test.table)
