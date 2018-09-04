(ns rems.cljs-tests
  (:require [doo.runner :refer-macros [doo-tests]]
            rems.test.administration.form
            rems.test.administration.license
            rems.test.administration.resource
            rems.test.administration.workflow
            rems.test.table
            rems.test.util))

(doo-tests 'rems.test.administration.form
           'rems.test.administration.license
           'rems.test.administration.resource
           'rems.test.administration.workflow
           'rems.test.table
           'rems.test.util)
