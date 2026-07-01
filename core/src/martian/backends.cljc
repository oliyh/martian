(ns martian.backends
  "Selection of the schema backend from Martian opts.

   This is the backend-neutral home for backend selection: it is about *opts*,
   not about schemas, so callers that only need to resolve a backend need not
   reach through `martian.schema` (and thereby the Plumatic backend). The
   default remains the Plumatic backend for backward compatibility."
  (:require [martian.backends.plumatic :as plumatic]))

(def default-backend
  "The default schema backend used when none is supplied in opts (Plumatic Schema)."
  plumatic/backend)

(defn get-backend
  "Returns the schema backend from opts, defaulting to the Plumatic backend."
  [opts]
  (get opts :schema-backend default-backend))
