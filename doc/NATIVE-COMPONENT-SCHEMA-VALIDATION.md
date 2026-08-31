# Native Component Schema Validation

> What was done, which native Ignition functions power it, and what we can do now — in plain terms.

| Attribute | Value |
|-----------|-------|
| Date | 2026-08-30 |
| Target | Ignition 8.3.7 · Perspective 3.3.7 |
| Module | `ignition-agent-tools` (Gateway) |
| Status | Implemented, compiled, 76/76 tests green |

---

## 1. What We Did (in one paragraph)

`POST /view/validate` already checked the *shape* of a Perspective view (component type present,
`meta`/`props` exist, children are arrays, flex layout basics, bound values look right). What it
**could not** do was check that a component's `props` actually matched what the component accepts.

Now it does. We load the **exact same prop schemas** Perspective ships in `perspective-common`
(the `*.components.json` files the Designer uses to validate props), hand them to **Ignition's own
JSON Schema validator**, and every component in a view gets its `props` checked against the real
schema: wrong types, bad enum values, unknown props (where the schema forbids them), and missing
required props now produce concrete errors (`SCHEMA_*` codes) with exact JSON paths.

Two things the native engine can't do on its own, so we added small helpers around it:

1. **`style`-style props are `$ref`s to other schema files** (e.g. `style` → `style-properties.schema.json`).
   Ignition's validator *silently skips* these `urn:ignition-schema:` refs when the schema is built by
   the factory (bytecode-verified). We pre-resolve each one into its own standalone schema so `style`
   still gets fully validated.
2. **Bound values are not literals.** `{"text": {"type": "expr", "config": {...}}}` isn't a string,
   but it's a valid *binding*. We skip binding-shaped values during type/enum checks so they don't
   false-fail on strictly-typed props (and structural binding checks still apply).

We also fixed a couple of pre-existing issues found along the way (a missing import in
`AgentToolsRouteHandlers.java`, and an outdated javadoc claiming we didn't reuse Perspective schemas).

---

## 2. Native Ignition Functions We Now Use

| Native function | What it does | Where we use it |
|---|---|---|
| `JsonSchemaFactory.getSchema(JsonElement)` | Compiles a JSON Schema into a real validator object (`JsonSchema`). It understands Perspective's schema format (types, enums, required, `additionalProperties`, `#/definitions/...` refs). | `PerspectiveComponentSchemaCatalog.java` — compiles every component's `props` schema, plus each resolved URN target. |
| `JsonSchema.validate(JsonElement instance, JsonElement rootNode, String pathPrefix)` | Runs a JSON value against the schema. Returns a `Set<ValidationMessage>` (empty = valid). | `PerspectiveComponentSchemaCatalogTest` (directly) and `PerspectiveViewValidator.checkComponentSchema` (via the catalog) — validates each component's `props` and each `style`-style prop. |
| `ValidationMessage.getType() / getPath() / getCode() / getMessage()` | Native violation accessors: the validator keyword (`required`, `enum`, `type`, `additionalProperties`…), the offending path, and the human message. | `PerspectiveViewValidator.addSchemaIssue` — converts each native message into a `ValidationIssue` (`SCHEMA_<KEYWORD>` code, ERROR severity). |
| `com.inductiveautomation.common.gson.JsonParser.parseString(...)` | Ignition's shaded Gson — parses the descriptor schemas and the incoming view JSON. | Catalog + validator. |

**Native resources reused (not recreated):** the descriptor files bundled in `perspective-common-3.3.7.jar`:

```
ia.components.json                      (~70 stock components)
barcode.component.json
perspective-timeseries.components.json (chartrangeselector, powerchart, timeseries)
perspective-googlemap.components.json
perspective-amcharts.components.json   (gauge, pie, simple-gauge, xy)
perspective-map.components.json
pdf-viewer.components.json
```

plus the referenced schema files, e.g. `schemas/style-properties.schema.json`,
`schemas/trend-style.schema.json`, `schemas/icon-schema.json`.

> We verified (not assumed) everything against the actual `common-8.3.7.jar` / `perspective-common`
> jars on disk, including the `RefValidator` bytecode that revealed the URN-ref skip. Full audit
> trail: `api-tracking/native-apis-used.md`.

### Investigated but deliberately not used
| API | Why not |
|---|---|
| `ComponentRegistry` / `ComponentDescriptor.schema()` | Exposes a compiled schema but **no raw schema element** (needed to pre-resolve URN refs) and drags in AWT/Swing icon plumbing. We read the identical files directly. |
| `ResourceValidator<R>` | Bare interface with **no gateway-side registry**; Perspective registers no view validator against it. The real view-validation path is the Designer's client-side `ValidationEngine`. |
| Designer `ValidationEngine` / client `JsonSchemaValidator` | **Designer/client-only** — not usable from a Gateway module. |

---

## 3. New Error Codes (`/view/validate`)

| Code | Meaning | Example fix |
|---|---|---|
| `SCHEMA_REQUIRED` | A required prop is missing | flex needs `direction`, `wrap`, `justify`, `alignItems`, `alignContent`, `style` |
| `SCHEMA_TYPE` | Value has the wrong type | `text-field.text` must be a string |
| `SCHEMA_ENUM` | Value not in the allowed list | flex `direction` must be `row`/`row-reverse`/`column`/`column-reverse` |
| `SCHEMA_ADDITIONALPROPERTIES` | Unknown prop on a schema with `additionalProperties:false` | remove the unknown key |
| `SCHEMA_<other>` | Any other native violation (format, pattern, min/max, …) | follow the message text |

All are `ERROR` severity and carry a JSON path (`$.root.children[0].props.style`, etc.).

---

## 4. What This Enables Now (simple terms)

- **An AI agent can get a real answer to "is this view valid?"** — not just well-formed, but matching
  what each component actually accepts. The `/view/validate` response now includes schema errors
  grounded in Perspective's own definitions.
- **Actionable fixes.** Every schema issue tells you the exact path, the broken rule, and (via the
  message) what to change.
- **Style props are checked too.** Passing a wrong value to `style` (e.g. `{"marginTop": "tall"}`)
  is caught, not silently ignored.
- **No false alarms on bindings.** `{"type": "expr", ...}` values are validated *as bindings*, not
  as literal strings.
- **Graceful everywhere.** Unknown/custom components or a gateway without Perspective just skip the
  schema pass — no crash, no bogus errors.
- **Proven by tests.** `PerspectiveComponentSchemaCatalogTest` + a new `SchemaValidation` suite in
  `PerspectiveViewValidatorTest`; existing fixtures were updated to schema-conformant `props`
  (e.g. flex now needs its 5 layout keys + `style`).
- **Documented for future work.** `api-tracking/` records exactly which native APIs we rely on and
  why the custom helpers exist, so nothing is re-derived from memory later.

---

## 5. Related Files

| File | Role |
|---|---|
| `gateway/src/main/java/com/axcend/ignition/agenttools/validate/PerspectiveComponentSchemaCatalog.java` | Loads `*.components.json` from classpath, indexes `props` schemas, resolves URN refs |
| `gateway/src/main/java/com/axcend/ignition/agenttools/validate/PerspectiveViewValidator.java` | Per-node `checkComponentSchema()` + `SCHEMA_*` issue mapping |
| `gateway/src/test/java/com/axcend/ignition/agenttools/PerspectiveComponentSchemaCatalogTest.java` | Catalog + native-validation tests |
| `gateway/src/test/java/com/axcend/ignition/agenttools/PerspectiveViewValidatorTest.java` | End-to-end validator tests (incl. `SchemaValidation`) |
| `api-tracking/native-apis-used.md` | Audit trail of the native APIs and decisions |