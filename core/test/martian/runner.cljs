(ns martian.runner
  (:require [doo.runner :refer-macros [doo-all-tests]]
            [martian.core-test]
            [martian.schema-test]))

(doo-all-tests #"^martian.*test$")
