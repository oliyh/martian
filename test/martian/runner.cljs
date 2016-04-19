(ns martian.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [martian.core-test]))

(doo-tests 'martian.core-test)
