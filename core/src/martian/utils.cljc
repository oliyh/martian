(ns martian.utils)


(defn string->int [s]
  #?(:clj (Long/parseLong s)
     :cljs (js/parseInt s)))
