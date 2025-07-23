(ns martian.encoding
  (:require [clojure.string :as str]))

;; Media Types

(defn choose-media-type [encoders options]
  (some (set options) (keys encoders)))

(defn get-type-subtype [media-type]
  (when (and (string? media-type) (not (str/blank? media-type)))
    (str/trim
      (if-some [params-sep-idx (str/index-of media-type \;)]
        (subs media-type 0 params-sep-idx)
        media-type))))

;; Encoder Selection

(def auto-encoder
  {:encode identity
   :decode identity})

(defn find-encoder [encoders media-type]
  (or (when-some [type-subtype (get-type-subtype media-type)]
        (get encoders type-subtype auto-encoder))
      auto-encoder))

;; Response Coercion

(defn set-default-coerce-opts
  "Returns an HTTP client-specific response coercion options, applying default
   values, if necessary:
   - `:skip-decoding-for`    — a set of media types for which the decoding can
                               be skipped in favor of the client built-in one
   - `:auto-coercion-pred`   — a pred of `coerce-as-value` that checks whether
                               client response auto-coercion has been applied
   - `:request-key`          — usually `:as`, though some clients expect other
                               keys, e.g. `:response-type` for the `cljs-http`
   - `:type-aliases`         — a mapping of client-specific (raw) type aliases
   - `:missing-encoder-as`   — for the case where the media type is missing or
                               when there is no encoder for the specified type
   - `:default-encoder-as`   — for in case the found encoder for the specified
                               media type omits its own `:as` value"
  [{:keys [skip-decoding-for auto-coercion-pred type-aliases
           request-key missing-encoder-as default-encoder-as]
    :or {missing-encoder-as :auto
         ;; NB: Better be `:auto` to leverage the built-in client coercions
         ;;     which are usually based on the Content-Type response header.
         ;;     Leaving `:string` (the same as `:text`) for backward compat.
         default-encoder-as :string}}]
  {:skip-decoding-for (or skip-decoding-for #{})
   :auto-coercion-pred (or auto-coercion-pred (constantly false))
   :request-key (or request-key :as)
   :type-aliases (or type-aliases {})
   ;; NB: Passing `nil` to any of these must be a valid option.
   :missing-encoder-as missing-encoder-as
   :default-encoder-as default-encoder-as})

(defn get-coerce-as
  [encoders media-type {:keys [type-aliases missing-encoder-as default-encoder-as]}]
  (let [encoder (find-encoder encoders media-type)]
    (if (= auto-encoder encoder)
      {:type :missing
       :value missing-encoder-as}
      (if-let [encoder-as (:as encoder)]
        {:type :encoder
         :value (get type-aliases encoder-as encoder-as)}
        {:type :default
         :value default-encoder-as}))))

(defn skip-decoding?
  "Checks whether a response decoding (by Martian) should be skipped.

   Skip only when the client did coerce a response to the final type,
   which may not be the case if the 'Accept' encoder had some custom
   (non-default) `:as` value, meaning it still expects to decode the
   response from this (intermediary) type to the final one."
  [{:keys [type] :as _coerce-as}
   content-type
   {:keys [skip-decoding-for] :as _coerce-opts}]
  (let [type-subtype (get-type-subtype content-type)]
    (and (contains? skip-decoding-for type-subtype)
         (not= :encoder type))))

(defn auto-coercion-by-client?
  "Checks if a response should have already gone through the client's
   auto-coercion (to avoid double coercion of the same sort).

   A workaround needed specifically for `clj-http` and `hato` clients
   with response auto-coercion being turned on e.g. due to a presence
   of the '*/*' response content in the OpenAPI/Swagger definition."
  [{:keys [type value] :as _coerce-as}
   {:keys [auto-coercion-pred] :as _coerce-opts}]
  (and (= :missing type)
       (auto-coercion-pred value)))
