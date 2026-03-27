(ns martian.schema-backend)

(defprotocol SchemaBackend
  "An abstraction over schema construction and runtime operations.
   Implement this protocol to support a different schema library."

  ;; Schema construction — leaf types
  (leaf-schema [backend property]
    "Returns a schema for the given OpenAPI property descriptor map.
     The property map contains :type, :enum, :format keys.")
  (any-schema [backend]
    "Returns the 'anything goes' schema (equivalent to s/Any).")
  (int-schema [backend]
    "Returns the integer schema (equivalent to s/Int). Used in range constraints.")

  ;; Schema combinators
  (maybe-schema [backend s]
    "Wraps schema s as optional/nullable (equivalent to s/maybe).")
  (optional-key [backend k]
    "Wraps key k as an optional map key (equivalent to s/optional-key).")
  (eq-schema [backend value]
    "Returns a schema that matches exactly value (equivalent to s/eq).")
  (constrained-schema [backend s pred]
    "Returns a schema that constrains s with pred (equivalent to s/constrained).")

  ;; Default value wrapping
  (with-default-value [backend property s]
    "Wraps schema s with a default value from property's :default key.
     Returns s unchanged if :default is not present.")

  ;; Collection format handling
  (wrap-collection-format-schema [backend array-schema collection-format]
    "Wraps an array schema to handle a specific collection format string
     (e.g. 'csv', 'ssv'). Returns array-schema unchanged if not applicable.")

  ;; Key inspection
  (unwrap-key [backend k]
    "Unwraps an optional/required key wrapper to return the plain key.
     Equivalent to s/explicit-schema-key for Plumatic Schema.")

  ;; Runtime operations
  (coerce-data [backend schema data opts]
    "Extracts and coerces data to match schema. Returns nil if schema is nil.
     opts may contain :coercion-matcher, :use-defaults?, :parameter-aliases.")
  (check-schema [backend schema value]
    "Checks value against schema. Returns nil if valid, error info otherwise.
     Equivalent to s/check.")
  (validate-schema [backend schema value]
    "Validates value against schema. Returns value if valid, throws otherwise.
     Equivalent to s/validate."))
