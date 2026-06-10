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

  ;; Schema construction — composites
  (map-schema [backend entries opts]
    "Returns a map schema for the given entries, each a map of:
     - :key       — a plain keyword key
     - :required? — whether the key must be present
     - :schema    — the value schema
     opts may contain:
     - :open?     — when true, the map accepts arbitrary additional keys
                    (OpenAPI 'additionalProperties').")
  (seq-schema [backend item-schema]
    "Returns a schema for a homogeneous sequence of item-schema values.")
  (maybe-schema [backend s]
    "Wraps schema s as optional/nullable (equivalent to s/maybe).")
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

  ;; Schema inspection
  (map-schema-keys [backend schema]
    "Returns the plain keys of a map schema in entry order, ignoring generic
     keys (e.g. the catch-all key of an open map). Returns nil if schema is
     not a map schema.")
  (merge-map-schemas [backend schemas]
    "Merges several map schemas into a single map schema, with the entries
     of later schemas winning.")
  (eq-schema-value [backend schema]
    "Returns the value matched by an eq-schema, or nil if schema is not one.")

  ;; Parameter aliases (idiomatic kebab-case keys)
  (key-paths [backend schema]
    "Returns a sequence of key paths (vectors of plain keys) covering the
     schema itself (the [] path) and every map entry reachable within it.")
  (aliases-at [backend schema idiomatic-path]
    "Returns a map of idiomatic (kebab-case) keys to original keys for the
     map level at idiomatic-path within schema, or nil when there are none.")
  (alias-schema [backend aliases schema]
    "Returns schema with the keys of it and its subschemas renamed to their
     idiomatic (kebab-case) forms using the given aliases registry.")

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
