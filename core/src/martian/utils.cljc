(ns martian.utils)

;; TODO: Substitute with `parse-long` (built-in, cross-platform).
(defn string->int [s]
  #?(:clj  (Long/parseLong s)
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

;; NB: Credits to the 'https://stackoverflow.com/a/26059795'.
(let [sentinel ::absent]
  (defn contains-in?
    [m ks]
    (not (identical? sentinel (get-in m ks sentinel)))))

(defn update-in*
  ([m ks f]
   (update-in* m ks f nil))
  ([m ks f args]
   (if (contains-in? m ks)
     (if (nil? args)
       (update-in m ks f)
       (apply update-in m ks f args))
     m)))

(defn stringify-named [obj]
  (if (or (keyword? obj) (symbol? obj))
    (if-some [ns (namespace obj)]
      (str ns "/" (name obj))
      (name obj))
    obj))
