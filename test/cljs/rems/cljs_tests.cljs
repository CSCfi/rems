(ns rems.cljs-tests
  (:require [doo.runner :refer-macros [doo-tests]]
            [rems.test.example]))

(doo-tests 'rems.test.example)
