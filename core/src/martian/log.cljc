(ns martian.log
  (:require #?(:clj [clojure.tools.logging :as log])))

#?(:clj
   (do
     (defmacro debug
       {:arglists '([message & more] [throwable message & more])}
       [& args]
       `(log/logp :debug ~@args))

     (defmacro error
       {:arglists '([message & more] [throwable message & more])}
       [& args]
       `(log/logp :error ~@args))

     (defmacro info
       {:arglists '([message & more] [throwable message & more])}
       [& args]
       `(log/logp :info ~@args))

     (defmacro warn
       {:arglists '([message & more] [throwable message & more])}
       [& args]
       `(log/logp :warn ~@args))
     ))

#?(:cljs
   (do
     (def
       ^{:arglists '([message & more] [throwable message & more])}
       debug
       js/console.debug)

     (def
       ^{:arglists '([message & more] [throwable message & more])}
       info
       js/console.info)

     (def
       ^{:arglists '([message & more] [throwable message & more])}
       warn
       js/console.warn)

     (def
       ^{:arglists '([message & more] [throwable message & more])}
       error
       js/console.error)
     ))
