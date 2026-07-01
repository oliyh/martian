# Review: schema-backend abstraction (Plumatic Schema ↔ Malli)

Reviewed from three points of view:

1. **Correctness** — is behaviour the same across backends?
2. **API** — is it just as clean to use Malli as Plumatic Schema?
3. **Readability & maintainability** — is the code as simple as it can be, and do
   the tests tell a story?

Guided by the repo's philosophy (pure functions, HTTP abstraction, simple-but-powerful
interceptor extensibility, multiple backends, declarative use) and Rich Hickey's
principles of decomplection.

## Verdict

This is high-quality, well-considered work. The core idea — a `SchemaBackend` protocol
that owns schema construction, introspection, aliasing, coercion and validation — is the
right seam, and it's executed with unusual discipline: symmetric backends, excellent
protocol docstrings, and a **cross-backend acceptance test battery** that encodes the
"behaves identically" contract as executable spec.

Correctness is solid: every test passes on both backends across Clojure and ClojureScript
(87 core + 14 martian-test tests, 0 failures), and hand-probed edge cases
(number/integer/boolean-from-string, extra-key stripping, open-map passthrough) produce
identical results.

Findings below are sorted High → Low. None are correctness defects; they concern
discoverability, decomplection, and a few residual seams.

---

## Correctness — is behaviour the same? ✅

Verified equivalent within the tested surface, and the tests are genuinely thorough:

- `martian.backends.acceptance-test` runs the *same* Swagger + OpenAPI specs through both
  backends asserting identical URLs, request maps, coercion, kebab-case aliasing, defaults,
  and response validation. This is the strongest part of the work — it's the contract, and
  it's enforced.
- `martian.test.acceptance-test` extends the same discipline to generative response stubbing.
- Extra probes (`number "1.5"→1.5`, `integer "42"→42`, `boolean "true"→true`, extra-key
  stripping, open-map passthrough) all matched across backends.
- The `keywordize-opts` fix (`core.cljc`) is a nice catch — `keywordize-keys` would
  otherwise walk the backend *record* into a plain map and silently destroy it. Good
  defensive design.

Minor coverage gaps (see L5) but no behavioural divergence found.

---

## HIGH

### H1 — The public `bootstrap-*` docstrings don't mention `:schema-backend` or `:transformer` (API discoverability)

`core.cljc` bootstrap docstrings still list only `:coercion-matcher` as the coercion knob,
presented as if universal. The entire value of this branch — choosing a backend — is
invisible in the canonical API reference (cljdoc, editor tooltips). A user reading
`(doc bootstrap-openapi)` has no idea Malli exists, and `:coercion-matcher` is silently a
no-op under Malli.

The README and migration guide cover this well, so the fix is cheap: add `:schema-backend`
to the bootstrap docstrings, and note that `:coercion-matcher` is Plumatic-only /
`:transformer` is the Malli equivalent. This directly serves the "just as clean to use
Malli" goal — the two backends should be equally discoverable at the API surface, not just
in prose docs.

---

## MEDIUM

### M1 — `aliases-hash-map` is backend-generic but reaches into Plumatic's `schema-tools` (decomplection leak)

`parameter_aliases.cljc` (the Babashka registry path) takes a `backend`, calls
`sb/key-paths backend`, but then idiomatizes with `schema-tools/->idiomatic` and
`schema-tools/idiomatic-path` — Plumatic-specific helpers that unwrap `s/optional-key`
wrappers. For Malli on Babashka this happens to work only because Malli's key-paths are
already plain keys (the unwrap is a no-op). That's exactly the coupling this branch set out
to remove: a generic function silently depending on one backend's representation. Use the
neutral `parameter-keys/->idiomatic` / `idiomatic-path` here (they operate on plain keys),
so the babashka path is truly backend-agnostic.

### M2 — The `SchemaBackend` protocol conflates four concerns (design / simplicity)

21 methods spanning construction (`leaf/any/int/map/seq/maybe/eq/constrained/with-default/
wrap-collection-format`), introspection (`map-schema-keys/merge-map-schemas/eq-schema-value`),
aliasing (`key-paths/aliases-at/alias-schema`), and runtime (`coerce/check/validate`). It's
cohesive ("everything about one schema library") and beautifully documented, so this is a
judgement call, not a defect — but from a decomplection standpoint these are four
independent capabilities braided into one interface. A partial implementer (e.g. someone who
only wants construction + coercion, no alias support) must stub the rest. If a third backend
ever lands, splitting into `SchemaConstruction` / `SchemaIntrospection` / `SchemaAliases` /
`SchemaRuntime` would let backends compose capabilities rather than implement a monolith.
Not worth doing for two backends today; worth a comment noting the intended seams.

### M3 — `int-schema` exists solely to feed one `constrained-schema` call

`openapi.cljc` builds `XX` status ranges as
`(constrained-schema backend (int-schema backend) range-pred)`. `int-schema` is a protocol
method with exactly one caller, widening the interface for a single internal need. Consider
having the backend expose a single `status-range-schema` (or letting `constrained-schema`
accept the range directly), so the "integer" primitive isn't part of the public backend
contract just to satisfy status-code plumbing.

---

## LOW

- **L1 — Trivial private wrapper.** `interceptors.cljc` `(defn- get-backend [opts]
  (schema/get-backend opts))` adds a name but no behaviour; it's called 4×. Inline
  `schema/get-backend` and drop it.
- **L2 — `unalias-data` imported from two namespaces.** The Plumatic backend pulls it from
  `martian.schema-tools`, the Malli backend from `martian.parameter-keys` — same function,
  re-exported. Point both at `parameter-keys` so the shared, backend-neutral helper has one
  obvious home.
- **L3 — `schema/get-backend` lives in `martian.schema`.** It's about *opts*, not schemas,
  yet reading it forces every caller (`core`, `interceptors`, `swagger`, `openapi`, `test`)
  through `martian.schema` (→ Plumatic). Harmless since Plumatic is the default, but a
  neutral home (e.g. the `schema-backend` ns) would keep the dependency direction cleaner.
- **L4 — `::input-schema` spec loosened to `any?`** (`spec.cljc`). Reasonable (representation
  is backend-owned) but it drops all validation. If you want to keep some teeth, the backend
  could expose a `schema?` predicate; otherwise the added comment is sufficient.
- **L5 — Coverage gaps in the acceptance battery.** OpenAPI path isn't exercised with
  `:use-defaults? true` (only Swagger is), and the `default` response status isn't asserted.
  Both backends behaved identically in manual probes, so this is about locking it in, not a
  suspected bug.
- **L6 — Malli recompiles schema forms per coercion.** `coerce-data` calls `m/coerce` on the
  stored *form* each request. Keeping forms (not compiled schemas) is the right call for
  readable `explore` output — and Plumatic's `sc/coercer!` also rebuilds per call, so it's
  **not a regression**. Noted only because Malli makes a one-time `m/schema` compile trivial
  if this path ever shows up in a profile.

---

## Readability & maintainability — do the tests tell a story? ✅

Strong. Highlights worth preserving:

- The two backends are near mirror-images with parallel method order and matching section
  banners — you can diff them by eye. Reads like the surrounding code.
- Protocol docstrings are exemplary: each method states its Plumatic equivalent
  (`equivalent to s/maybe`), which is the perfect Rosetta stone for a second implementer.
- The Malli backend leans on `m/type` / `m/children` / `mu/subschemas` rather than
  hand-parsing vector forms — the robust, future-proof choice, and the docstring says why.
- **The acceptance tests genuinely narrate behaviour** — `testing` strings like "drops keys
  that are not in the schema", "coerces body params, renaming aliased keys", "throws when a
  required body key is missing" read as a spec of Martian's contract, independent of
  backend. This is the model the rest of the suite should aspire to.
- `martian.test.generators` extending backends *outside* the core protocol (to keep
  test.check / malli.generator deps inside `martian-test`) is a tasteful boundary decision,
  and the ns docstring explains the reasoning.

Net: the abstraction is at the right altitude, correctness is verified and enforced by
tests, and the residual issues are mostly discoverability and a couple of coupling seams —
all cheap to close. The single most valuable follow-up is **H1** (surface `:schema-backend`
in the public docstrings) so Malli is as discoverable as it is usable.
