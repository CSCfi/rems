(ns rems.cljs-tests
  (:require [doo.runner :refer-macros [doo-tests]]
            [rems.test.administration.licenses]
            [rems.test.table]))

(doo-tests 'rems.test.administration.licenses
           'rems.test.table)
