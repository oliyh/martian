# Migrating from Plumatic Schema to Malli

Martian validates and coerces parameters using a pluggable schema backend. The default backend uses
[Plumatic Schema](https://github.com/plumatic/schema); a [Malli](https://github.com/metosin/malli)
backend is also available. Both backends produce identical Martian behaviour for the same
OpenAPI/Swagger spec — URLs, request maps, parameter coercion, idiomatic (kebab-case) parameters,
defaults and response validation all work the same. What changes is the schema representation and
the way custom coercions are expressed.

## Selecting the Malli backend

Pass the backend in the opts of any bootstrap function:

```clojure
(require '[martian.core :as martian]
         '[martian.backends.malli :as malli])

(martian/bootstrap-openapi "https://api.org" openapi-spec
                           {:schema-backend malli/backend})
```

That's it — calls like `url-for`, `request-for` and `response-for` behave exactly as before:

```clojure
(martian/request-for m :create-pet {:pet {:id 123 :name "charlie"}})
;; => {:method :post, :url "https://api.org/pets/", :body {:id 123, :name "charlie"}}
```

## What changes

### Handler schemas are Malli forms

Handler schemas (`:path-schema`, `:query-schema`, `:body-schema`, etc.) and everything shown by
`martian.core/explore` are plain Malli vector forms instead of Plumatic schema values:

```clojure
;; Plumatic backend
(:parameters (martian/explore m :create-pet))
;; => {:pet {:id s/Int, (s/optional-key :name) (s/maybe s/Str)}}

;; Malli backend
(:parameters (martian/explore m :create-pet))
;; => [:map [:pet [:map [:id :int] [:name {:optional true} [:maybe :string]]]]]
```

Being plain data, these forms can be used directly with any `malli.core` function.

### Hand-written schemas in `bootstrap`

If you build a Martian instance from data with `martian.core/bootstrap`, write the parameter
schemas as Malli forms:

```clojure
;; Plumatic backend
(martian/bootstrap "https://api.org"
                   [{:route-name  :load-pet
                     :path-parts  ["/pets/" :id]
                     :method      :get
                     :path-schema {:id s/Int}}])

;; Malli backend
(martian/bootstrap "https://api.org"
                   [{:route-name  :load-pet
                     :path-parts  ["/pets/" :id]
                     :method      :get
                     :path-schema [:map [:id :int]]}]
                   {:schema-backend malli/backend})
```

### Custom coercion: `:coercion-matcher` becomes `:transformer`

The `:coercion-matcher` opt is specific to the Plumatic backend. With the Malli backend, custom
coercion is expressed as a [Malli transformer](https://github.com/metosin/malli#value-transformation)
passed via the `:transformer` opt, which replaces the backend's default transformer:

```clojure
(require '[malli.transform :as mt]
         '[martian.backends.malli :as malli])

(martian/bootstrap-openapi
  "https://api.org" openapi-spec
  {:schema-backend malli/backend
   ;; composing with the default transformer keeps the standard behaviour
   :transformer (mt/transformer malli/default-transformer
                                my-extra-transformer)})
```

`martian.backends.malli/default-transformer` replicates the behaviour of the default Plumatic
coercion matcher: it strips keys that are not in the schema, coerces strings to the schema's leaf
types (e.g. `"123"` to `123`), turns keywords into strings, and joins arrays that have a Swagger
`collectionFormat` (e.g. `csv`).

### Validation errors

Coercion and response validation failures throw `ex-info` exceptions carrying Malli explanations
(humanized via `malli.error/humanize`) instead of Plumatic coercion errors. If your code matches on
error messages or `ex-data`, it will need updating.

## What stays the same

- All bootstrap functions and the rest of the `martian.core` API
- Idiomatic (kebab-case) parameter names, e.g. passing `:foo-bar` for a parameter called `FooBar`
- Parameter defaults with `:use-defaults? true`
- The `martian.interceptors/validate-response-body` interceptor
- The default interceptor stacks of all HTTP client modules
- `martian-test` response stubbing, including responses generated from response schemas

The contract is enforced by an acceptance test battery (`martian.backends.acceptance-test`) that
runs the same specs through every backend and asserts identical results.

## Caveats

- The Plumatic Schema libraries remain dependencies of the Martian core, so switching backends does
  not remove them from your classpath.
- The `martian.schema` namespace's compatibility functions (`leaf-schema`, `coerce-data`, etc.)
  always use the Plumatic backend; backend-aware code should use `martian.schema-backend` instead.
