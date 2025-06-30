(ns martian.utils)


(defn string->int [s]
  #?(:clj (Long/parseLong s)
     :cljs (js/parseInt s)))

(defn update*
  ([m k f]
   (update* m k f nil))
  ([m k f & args]
   (if (contains? m k)
     (if (seq args)
       (apply update m k f args)
       (update m k f))
     m)))
