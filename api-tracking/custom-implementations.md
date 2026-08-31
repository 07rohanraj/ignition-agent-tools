# Custom Implementations (No Native API Used)

This file documents features implemented with custom code because no suitable native Ignition API was found (or was missed at implementation time).

**Last Updated:** 2026-08-30
**Ignition Version:** 8.3.7

---

## Expression Binding Validation — Content Checks

> The expression's **grammar** is validated natively by `ELParserHarness` + `DefaultFunctionFactory`
> (see `native-apis-used.md`, adopted 2026-08-29). The checks below are the content-level heuristics
> the native parser accepts but are still problematic. They are **centralized** in
> `IgnitionExpressionValidator` (single source of truth); `DiagnosticService` no longer duplicates them.

| Feature | File:Line | Custom Logic | Native Alternative? |
|---|---|---|---|
| Python import detection | `IgnitionExpressionValidator.java:186-192` | `expression.contains("from ") && expression.contains(" import ")` | Parser reports a generic `Syntax Error` but no structured import/AST API |
| Client-scope function detection | `IgnitionExpressionValidator.java:195-201` | `contains("system.perspective") \|\| contains("system.gui") \|\| contains("system.nav")` | No public SDK API for function scope classification |
| runScript() format validation | `IgnitionExpressionValidator.java:243-282` | Regex: `runScript\s*\(\s*['\"]([^'\"]+)['\"]\s*,\s*(.*?)\s*\)` + checks for `.` in module path, pollRate ≥ 0, pollRate is number | `ExpressionFunctionManager` — internal, not public SDK; the runScript call itself parses natively |
| Expression suggestions (empty / dangling operator) | `IgnitionExpressionValidator.java:284-299` | Heuristic: empty expr → 4 templates; trailing `+` → "Complete the expression" | None — parser only throws exception |

---

## Tag Binding Validation — Semantic Checks

| Feature | File:Line | Custom Logic | Native Alternative? |
|---|---|---|---|
| Tag existence + quality | ~~`DiagnosticService.java` (removed)~~ | ~~`TagPathParser.parseSafe()` → `provider.readAsync()` → check `QualityCode.isGood()`~~ | ✅ **RESOLVED (2026-08-29)** — adopted native `TagPathValidator` in `DiagnosticService.checkTagPath()`; no custom semantic tag validation remains |

---

## Named Query Binding Validation

| Feature | File:Line | Custom Logic | Native Alternative? |
|---|---|---|---|
| Query existence | `DiagnosticService.java:1132` | `NamedQueryManager` lookup | Native — this uses `NamedQueryManager` |
| Parameter validation | `DiagnosticService.java:1140-1160` | Check `parameters` is object, `pollRate` ≥ 0 | Native — uses `NamedQueryManager` |

---

## JSON/Config Validation — Hand-Written Rules

| Feature | File:Line | Custom Logic | Native Alternative? |
|---|---|---|---|
| View/component property presence | `PerspectiveViewValidator.java`, `DiagnosticService.java` | ~~`if (!props.has("propName"))`~~ ✅ **RESOLVED (2026-08-30)** — retargeted to native schemas where available: `PerspectiveComponentSchemaCatalog` loads the Perspective component `*.components.json` prop schemas and `PerspectiveViewValidator.checkComponentSchema` validates each component's `props` via native `JsonSchema`; hand-written structural checks remain for view/component-tree shape (children, meta, layout, bindings), not prop types |
| URN-ref prop resolution for schema validation | `PerspectiveComponentSchemaCatalog.java` (`resolveRefProps`/`resolveUrn`) | **Custom layer over native API** — native `RefValidator` skips `urn:ignition-schema:` refs on factory-built schemas (only checks `getSubSchema()`, which is null, then silently no-ops). Custom code loads each URN target from classpath and builds a standalone native `JsonSchema`, so `style`/`textStyle`/etc. props still get checked natively. |
| Binding-value exemption from prop type checks | `PerspectiveViewValidator.java` (`isBindingValue`/`propsWithoutBindings`) | **Custom filter** — a binding object (`{type: expr, config: {...}}`) is not a literal of the prop's declared type (e.g. `text-field.text` is `type: string` only), so binding-shaped values are excluded before schema type/enum checks to avoid false positives |
| Binding config structure | `DiagnosticService.java:640-750` | Manual JSON field checks for `type`, `config`, `transforms` | `BindingConfig` class exists but no parser |
| Component type validation | `ComponentCatalog.java` | Custom catalog of known types | None — Perspective component registry is internal |

---

## Binding Format Detection

| Feature | File:Line | Custom Logic | Native Alternative? |
|---|---|---|---|
| Inline shorthand `{tagPath}` vs `{binding:{type,config}}` | `DiagnosticService.java:400-500` | String prefix check + JSON parse attempt | `BindingConfig` is plain data class, no factory/parser |

---

## Diagnostic Error Container

| Feature | File:Line | Custom Logic | Native Alternative? |
|---|---|---|---|
| `DiagnosticIssue` record + `toMap()` | `DiagnosticIssue.java` | Custom record with `code, severity, category, message, details, path, suggestions` | **`ValidationErrors`**, `ValidationErrors.Builder`, `ValidationException` (in `gateway-api`) — **DECISION (2026-08-29)**: native container is message/field-only and lacks `code`/`severity`/`category`/`suggestions` the AI wire contract needs. Adopted as an **adapter** (`ValidationErrorsMapper`) for interop while keeping `DiagnosticIssue` for the wire format. |

---

## Notes

Several of these custom implementations had native alternatives that were **missed** during initial implementation. Progress:
- **TagPathValidator** → ✅ **DONE (2026-08-29)** replaces tag existence + quality logic
- **Expression/ExpressionParseContext** → ✅ **DONE (2026-08-29)** `ELParserHarness` + `DefaultFunctionFactory` now drive expression grammar validation; only content-level heuristics remain custom
- **ValidationErrors** → ✅ **DONE (2026-08-29)** as adapter (`ValidationErrorsMapper`) for gateway interop; `DiagnosticIssue` kept as AI-facing wire (native model lacks code/severity/suggestions)
- **JsonSchema** → ✅ **DONE (2026-08-30)** native Perspective component `props` schemas now drive prop validation (`PerspectiveComponentSchemaCatalog` + `checkComponentSchema`); hand-written prop-presence checks are no longer needed for known components. Remaining custom layers are (1) URN-ref pre-resolution (native engine skips them on factory-built schemas) and (2) the binding-value exemption filter.