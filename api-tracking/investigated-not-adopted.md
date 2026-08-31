# Native APIs Investigated but NOT Adopted

This file documents Ignition SDK APIs that were investigated for use but not adopted, with reasons.

**Last Updated:** 2026-08-30
**Ignition Version:** 8.3.7

---

## Should Be Adopted (High Priority)

### ValidationErrors / ValidationErrors.Builder / ValidationException
| Detail | Value |
|---|---|
| **Classes** | `com.inductiveautomation.ignition.gateway.config.ValidationErrors`<br>`com.inductiveautomation.ignition.gateway.config.ValidationErrors.Builder`<br>`com.inductiveautomation.ignition.gateway.config.ValidationException` |
| **Package** | `com.inductiveautomation.ignition.gateway.config` |
| **Location** | `gateway-api-8.3.7.jar` (compileOnly) |
| **Key Features** | GSON-serializable, `FieldValidationErrors` for field-level errors, `Builder` pattern |
| **Why Not Adopted** | Not yet integrated — `DiagnosticIssue` was created first |
| **Action** | Replace `DiagnosticIssue` with `ValidationErrors` for native error format |

### JsonSchema / JsonSchemaFactory
| Detail | Value |
|---|---|
| **Classes** | `com.inductiveautomation.ignition.common.jsonschema.JsonSchema`<br>`com.inductiveautomation.ignition.common.jsonschema.JsonSchemaFactory` |
| **Package** | `com.inductiveautomation.ignition.common.jsonschema` |
| **Location** | `common.jar` (runtime) |
| **Key Methods** | `JsonSchema.parse(InputStream)`<br>`Set<ValidationMessage> validate(JsonElement, JsonElement, String)` |
| **Features** | Full JSON Schema draft support: properties, items, types, enums, formats, defaults, examples, suggestions, deprecated, subschemas |
| **Why Not Adopted** | ✅ **RESOLVED (2026-08-30) — ADOPTED.** Powers `PerspectiveComponentSchemaCatalog` + `PerspectiveViewValidator.checkComponentSchema` against the native Perspective component `props` schemas (`*.components.json`). Note: native `RefValidator` silently skips `urn:ignition-schema:` refs on factory-built schemas, so URN-ref props (`style`, `textStyle`, ...) are pre-resolved into standalone native schemas. |
| **Action** | ✅ Completed |

### ComponentRegistry / ComponentDescriptor.schema()
| Detail | Value |
|---|---|
| **Classes** | `com.inductiveautomation.perspective.common.api.ComponentRegistry`<br>`com.inductiveautomation.perspective.common.api.ComponentDescriptor` |
| **Package** | `com.inductiveautomation.perspective.common.api` |
| **Location** | `perspective-common-3.3.7.jar` (runtime) |
| **Key Features** | Loads every `*.components.json` from the classpath; `ComponentDescriptor.schema()` returns a native `JsonSchema` built via `new JsonSchemaBuilder().withNode(schema).build()` |
| **Why Not Adopted** | `ComponentDescriptor` exposes no raw schema element (needed to pre-resolve URN refs) and drags in AWT/Swing icon plumbing. Custom catalog reads the identical classpath resources directly. |
| **Action** | Revisit only if a future native API surfaces the raw schema element |

### RefValidator (native JSON Schema `$ref` engine)
| Detail | Value |
|---|---|
| **Class** | `com.inductiveautomation.ignition.common.jsonschema.validators.RefValidator` |
| **Package** | `com.inductiveautomation.ignition.common.jsonschema.validators` |
| **Location** | `common-8.3.7.jar` (runtime) |
| **Key Behavior** | `#/definitions/*` refs resolve natively; `urn:ignition-schema:...` refs only try `jsonSchema.getSubSchema()` (null for factory-built schemas) → logged "Failed to resolve schema ref" and **silently skipped**; general external refs resolve via URL/TCCL |
| **Why Not Adopted** | Can't treat URN refs as skip — those props (e.g. `style`) are exactly what we want validated. Catalog pre-resolves URN targets into standalone schemas instead. |

### ResourceValidator
| Detail | Value |
|---|---|
| **Class** | `com.inductiveautomation.ignition.gateway.config.ResourceValidator<R>` |
| **Package** | `com.inductiveautomation.ignition.gateway.config` |
| **Location** | `gateway-api-8.3.7.jar` (compileOnly) |
| **Key Method** | `Optional<ValidationErrors> validate(R resource)` |
| **Why Not Adopted** | ✅ **RESOLVED (2026-08-30)** — bare functional interface. Bytecode/classpath audit: no registry or lookup for it exists in any gateway-side jar (`gateway-api`, `ignition-common`, `designer`, `perspective-gateway` all confirmed), `DefaultResourceTypeMeta` has no validator slot, and Perspective registers no view-resource validator on the gateway. The real view-validation path is the Designer's client-side `ValidationEngine` (not gateway-safe). Native `JsonSchema`-based component `props` validation is the correct gateway-safe path. |
| **Action** | ✅ Closed — no native gateway-side view-resource validator exists |

---

## Partially Usable (Need Verification)

### Expression / ExpressionParseContext / ExpressionFunctionManager
| Detail | Value |
|---|---|
| **Classes** | `com.inductiveautomation.ignition.common.expressions.Expression`<br>`com.inductiveautomation.ignition.common.expressions.ExpressionParseContext`<br>`com.inductiveautomation.ignition.common.expressions.ExpressionFunctionManager` |
| **Package** | `com.inductiveautomation.ignition.common.expressions` |
| **Location** | `common.jar` (runtime) |
| **Key Features** | `Expression.execute()`, `ExpressionParseContext`, function registration/validation |
| **Uncertainty** | **Unknown if Perspective expressions use this engine** — Perspective may use Jython-based parser (`PerspectiveExpression`) instead |
| **Why Not Adopted** | Need to verify which expression engine Perspective bindings use |
| **Action** | Test if `Expression` parser can parse Perspective expressions; if yes, use for structured analysis |

### TagPathParser (already used)
| Detail | Value |
|---|---|
| **Class** | `com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser` |
| **Status** | **Already adopted** — used in `DiagnosticService` and `GatewayIntrospectionService` |
| **Note** | Only does syntax parsing; `TagPathValidator` does semantic validation |

---

## Designer/Client-Only (Cannot Use in Gateway Module)

| API | Package | Reason |
|---|---|---|
| `ValidationEngine<T>` | `com.inductiveautomation.ignition.designer.gui.validation` | Designer-only — not on Gateway classpath |
| `Validator<T>` | `com.inductiveautomation.ignition.designer.gui.validation` | Designer-only |
| `ExpressionValueRequiredValidator` | `com.inductiveautomation.ignition.designer.gui.validation` | Designer-only |
| `ExpressionValueSyntaxValidator` | `com.inductiveautomation.ignition.designer.gui.validation` | Designer-only |
| `StringPathValidator` | `com.inductiveautomation.ignition.designer.gui.validation` | Designer-only |
| `StringRequiredValidator` | `com.inductiveautomation.ignition.designer.gui.validation` | Designer-only |
| `JsonSchemaValidator` | `com.inductiveautomation.ignition.client.jsonedit` | Client/Designer-only |
| `DocumentValidator` | `com.inductiveautomation.ignition.client.jsonedit` | Client/Designer-only |

---

## Not Yet Investigated

| API | Package | Potential Use |
|---|---|---|
| `BindingDiagnostic` | `com.inductiveautomation.perspective.gateway.binding` | Binding-specific diagnostics from Perspective |
| `PerspectiveElement` | `com.inductiveautomation.perspective.gateway.api` | Component tree navigation |
| `TagConfiguration` | `com.inductiveautomation.ignition.common.tags.config` | Tag definition validation |

---

## Decision Log

| Date | API | Decision | Rationale |
|---|---|---|---|
| 2026-08-28 | `TagPathValidator` | **Deferred — should adopt** | Missed during initial implementation; directly replaces custom tag validation |
| 2026-08-29 | `TagPathValidator` | **ADOPTED** | Replaced custom parse + `readAsync` + quality logic with native `TagPathValidator` in `DiagnosticService.checkTagPath()` |
| 2026-08-28 | `ValidationErrors` | **Deferred — should adopt** | Native structured error format, GSON-serializable |
| 2026-08-28 | `JsonSchema` | **Deferred — should adopt** | Could replace hundreds of lines of hand-written validation |
| 2026-08-30 | `JsonSchema` | **ADOPTED** | `PerspectiveComponentSchemaCatalog` + `PerspectiveViewValidator.checkComponentSchema` validate component `props` against the native `*.components.json` schemas; URN-ref props resolved standalone due to `RefValidator` skipping them |
| 2026-08-30 | `ComponentRegistry`/`ComponentDescriptor.schema()` | **Rejected — custom catalog instead** | No raw schema element exposed; AWT icon plumbing; custom loader reads the same resources |
| 2026-08-30 | `ResourceValidator` | **Rejected — no gateway path** | Interface-only; no registry on the gateway, Perspective registers no view validator; real path is Designer client-side `ValidationEngine` |
| 2026-08-28 | `ValidationEngine` / `Validator<T>` | **Rejected — Designer-only** | Gateway module cannot use Designer APIs |
| 2026-08-28 | `Expression` | **Deferred — verify engine** | Unknown if Perspective uses same expression engine |
| 2026-08-29 | `Expression`/`ExpressionParseContext` | **ADOPTED** | `ELParserHarness` + `DefaultFunctionFactory` drive expression grammar validation |