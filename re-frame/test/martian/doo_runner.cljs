(ns martian.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [martian.re-frame-test]))

(doo-tests 'martian.re-frame-test)
