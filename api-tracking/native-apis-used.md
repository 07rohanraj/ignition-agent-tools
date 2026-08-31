# API Usage Tracking

This directory records which Ignition-native APIs are used, which features are implemented with custom code, and which native APIs were investigated but not adopted.

**Last Updated:** 2026-08-30
**Ignition Version:** 8.3.7
**Module:** ignition-agent-tools (Gateway)

---

## 1. Ignition-Native APIs Actually Used

### Tag Path Parsing & Validation
| Class / Method | Purpose | Location | Status |
|---|---|---|---|
| `com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser.parseSafe(String)` | Parse tag path string to `TagPath` object, extract provider source | `GatewayIntrospectionService.java:81,138`<br>`DiagnosticService.java` (still used for provider lookup elsewhere) | ✅ Verified working — **audit (2026-08-30)**: kept; this is syntax-only parsing of user-supplied paths for browse/read (existence is verified by the provider call itself), not semantic validation, so `TagPathValidator` is not a replacement here |
| `com.inductiveautomation.ignition.common.tags.paths.TagPathValidator(TagManager)` + `validate(List<String>)` | **Native semantic tag-path validation** — resolves each path against the live tag manager and reports `Quality` (`GOOD`/syntax/existence/validation errors) + message. **Replaces** the previous custom parse + `readAsync` + quality check. | `DiagnosticService.java:60-95` (`getTagPathValidator`, `checkTagPath`), used in `validateTagBindingConfig` and `validateTagBinding` | ✅ **Adopted** (native) |
| `com.inductiveautomation.ignition.common.tags.paths.TagPathValidator.Quality` / `ValidatedTagPath` | Result types from the native validator | `DiagnosticService.java:87-95` | ✅ Verified working |
| `com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager` | Tag manager passed to `TagPathValidator` constructor | `DiagnosticService.java:68-74`<br>`GatewayIntrospectionService.java` | ✅ Verified working |
| `com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager.getTagProvider(String)` | Get provider by name | `GatewayIntrospectionService.java:278-283` | ✅ Verified working |
| `com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager.getTagProviderNames()` | List all tag providers | `GatewayIntrospectionService.java:61-63,71-72` | ✅ Verified working |

### Named Query Management
| Class / Method | Purpose | Location | Status |
|---|---|---|---|
| `com.inductiveautomation.ignition.common.db.namedquery.NamedQueryManager` | Validate named query existence | `DiagnosticService.java:1132`<br>`GatewayIntrospectionService.java:16,188` | ✅ Verified working |

### Perspective Expression Parsing
| Class / Method | Purpose | Location | Status |
|---|---|---|---|
| `com.inductiveautomation.ignition.common.expressions.parsing.ELParserHarness.parse(String, ExpressionParseContext)` | **Native expression grammar parser** — the exact engine Perspective uses for bindings. Parses arithmetic, `{tagRefs}`, `{view.params.x}`, `if(...)`, `max(...)`, and `runScript(...)` statically; flags `Syntax Error`/`Scan Error` on genuine token/grammar errors. **Replaces** `PerspectiveExpression.create()` (which requires a live `BindingContext` and NPEs without one). | `IgnitionExpressionValidator.java:213` | ✅ Adopted (native) |
| `com.inductiveautomation.ignition.common.expressions.DefaultFunctionFactory.getSharedInstance()` | Standard Ignition function set (`if`, `max`, arithmetic) backing the parse context | `IgnitionExpressionValidator.java:162` | ✅ Adopted (native) |
| `com.inductiveautomation.ignition.common.expressions.ExpressionParseContext` | Static parse context (marker expressions for bound refs, `DefaultFunctionFactory` for functions) | `IgnitionExpressionValidator.java:112-144` | ✅ Adopted (native) |

### Gateway Context & Module Lifecycle
| Class / Method | Purpose | Location | Status |
|---|---|---|---|
| `com.inductiveautomation.ignition.common.gateway.AbstractGatewayModuleHook` | Module hook base class | `AgentToolsHook.java` | ✅ Verified working |
| `com.inductiveautomation.ignition.common.gateway.GatewayContext` | Access to tag manager, named query manager, etc. | Multiple files | ✅ Verified working |
| `com.inductiveautomation.ignition.common.gateway.RouteGroup` | REST route registration | `AgentToolsRouteHandlers.java` | ✅ Verified working |
| `com.inductiveautomation.ignition.common.scripting.ScriptManager` | Jython script execution | `GatewayScriptService.java` | ✅ Verified working |

### JSON/Configuration
| Class / Method | Purpose | Location | Status |
|---|---|---|---|
| `com.inductiveautomation.ignition.common.gson.JsonObject` / `JsonArray` / `JsonElement` | JSON parsing/manipulation | Multiple files | ✅ Verified working |

### Native Perspective Component Schema Validation
| Class / Method | Purpose | Location | Status |
|---|---|---|---|
| `com.inductiveautomation.ignition.common.jsonschema.JsonSchema` / `JsonSchemaFactory` (`.getSchema(JsonElement)`) | Compile the native Perspective component `props` schemas (from `*.components.json`) into validators; run component `props` objects through them | `PerspectiveComponentSchemaCatalog.java` (`factory.getSchema`), `PerspectiveViewValidator.checkComponentSchema` | ✅ **Adopted (2026-08-30)** |
| `com.inductiveautomation.ignition.common.jsonschema.ValidationMessage` (`getType`, `getCode`, `getPath`, `getMessage`) | Map each native schema violation into a `ValidationIssue` (`SCHEMA_*` codes) | `PerspectiveViewValidator.addSchemaIssue` | ✅ Adopted |
| `com.inductiveautomation.perspective.common.api.ComponentRegistry` / `ComponentDescriptor.schema()` | Loader for the same `*.components.json` files; verifies a native `JsonSchema` is produced via `new JsonSchemaBuilder().withNode(schema).build()` | Investigated; **not used** in favor of the custom catalog (see §3) | ⚠️ Investigated-only |
| `com.inductiveautomation.ignition.common.jsonschema.JsonSchema.getRefSchemaNode(String)` | Would resolve `urn:ignition-schema:...` refs via TCCL `getResource("schemas/<file>")`; confirmed NOT called by `RefValidator`'s URN branch | Investigated (bytecode audit of `common-8.3.7.jar`) | ⚠️ Investigated-only |

### Native Validation Container Adapter
| Class / Method | Purpose | Location | Status |
|---|---|---|---|
| `com.inductiveautomation.ignition.gateway.config.ValidationErrors` / `ValidationErrors.newBuilder()` / `Builder.addFieldMessage` / `Builder.addMessage` / `build()` | Convert module issues into Ignition's native validation container for gateway-consumer interop (message/field-oriented). Native model has no `code`/`severity`/`suggestions`, so it is an **adapter** alongside (not a replacement of) the AI-facing `DiagnosticIssue` wire contract. | `ValidationErrorsMapper.java:47-49` | ✅ **Adopted (2026-08-29)** — adapter |
| `com.inductiveautomation.ignition.gateway.config.ValidationException` + `getValidationErrors()` | Native carrier signaling validation failure across module boundaries | `ValidationErrorsMapper.java:78` | ✅ **Adopted (2026-08-29)** — adapter |
| `DiagnosticService.getViewNativeValidationErrors(String,String)` | Run full view diagnostics and expose them as native `ValidationErrors` | `DiagnosticService.java:157-163` | ✅ Adopted |
| Optional `native` flag on `/diagnostics/view` | Emit `nativeErrors` (standard Ignition shape) alongside the AI-facing payload when requested | `AgentToolsRouteHandlers.java:289-293` | ✅ Adopted |

---

## 2. Features Implemented with Custom Code (No Native API Used)

### Expression Binding Content Validation
| Feature | Custom Implementation | Native API Investigated | Why No Native API |
|---|---|---|---|
| Python import detection (`from x import y`) | Regex `contains("from ") && contains(" import ")` in `IgnitionExpressionValidator.java:186-192` | `ELParserHarness` / `ExpressionParseContext` | The parser reports a generic `Syntax Error` for imports but with no structured AST/import inspection API, so a dedicated, actionable code (`PYTHON_IMPORT_IN_EXPRESSION`) is produced via content check. **Centralized here (single source of truth)** — no longer duplicated in `DiagnosticService`. |
| Client-scope function detection (`system.perspective`, `system.gui`, `system.nav`) | String `contains()` checks in `IgnitionExpressionValidator.java:195-201` | Same as above | No public SDK API to classify functions as gateway-vs-client scope. Returns early (warning) because these always contain `.`, which the grammar also rejects with a confusing `Scan Error`. **Centralized here.** |
| runScript() format validation (module path must contain `.`, pollRate ≥ 0, pollRate is number) | Regex `runScript\s*\(\s*['\"]([^'\"]+)['\"]\s*,\s*(.*?)\s*\)` in `IgnitionExpressionValidator.java:243-282` | Same as above | No public API for expression function argument semantic validation. `ExpressionFunctionManager` is internal. The call *itself* parses natively; only argument validation is custom. **Centralized here.** |
| Empty/missing expression suggestions | Heuristic suggestions in `suggestionIssues()` | `ELParserHarness` parse result | Parser only throws an exception; no structured suggestions API. |

### Tag Path Semantic Validation
| Feature | Custom Implementation | Native API Investigated | Why No Native API |
|---|---|---|---|
| Tag existence check + quality assessment | ~~Manual `provider.readAsync()` + quality check~~ | **RESOLVED** — `com.inductiveautomation.ignition.common.tags.paths.TagPathValidator` adopted in `DiagnosticService.checkTagPath()`. No custom tag semantic validation remains. | N/A — native API now used |

### Resource/JSON Validation
| Feature | Custom Implementation | Native API Investigated | Why No Native API |
|---|---|---|---|
| JSON schema validation for view/component config | Hand-written `if (!props.has(...))` checks in `DiagnosticService`, `PerspectiveViewValidator` | `com.inductiveautomation.ignition.common.jsonschema.JsonSchema`, `JsonSchemaFactory` | **RESOLVED (2026-08-30)** — `PerspectiveComponentSchemaCatalog` now loads the native Perspective component `props` schemas and `PerspectiveViewValidator.checkComponentSchema` runs each component's `props` through the native engine. Custom check remains only for the URN-ref resolution layer (native `RefValidator` silently skips `urn:ignition-schema:` refs on factory-built schemas — see §3) and binding-value filtering. |
| Structured validation error container | Custom `DiagnosticIssue` record + `toMap()` in `DiagnosticIssue.java` | `com.inductiveautomation.ignition.gateway.config.ValidationErrors`, `ValidationErrors.Builder`, `ValidationException` | Not yet integrated. Native container is GSON-serializable. |

### Binding Format Detection
| Feature | Custom Implementation | Native API Investigated | Why No Native API |
|---|---|---|---|
| Detect inline `{tagPath}` vs proper `{binding:{type,config}}` format | String parsing + JSON detection in `DiagnosticService.validateBinding()` | `com.inductiveautomation.perspective.common.config.BindingConfig` | `BindingConfig` is a plain data class with no `fromJson` factory. No public parser for shorthand format. |

---

## 3. Candidate Native APIs Investigated but NOT Adopted

| API | Package | Reason Not Adopted |
|---|---|---|
| `TagPathValidator` | `com.inductiveautomation.ignition.common.tags.paths` | **ADOPTED (2026-08-29)** — native tag semantic validation in `DiagnosticService.checkTagPath()`. |
| `ValidationErrors` / `ValidationErrors.Builder` / `ValidationException` | `com.inductiveautomation.ignition.gateway.config` | **ADOPTED as adapter (2026-08-29)** — native structured error container; used via `ValidationErrorsMapper` for interop while keeping the AI-facing `DiagnosticIssue` wire contract. |
| `JsonSchema` / `JsonSchemaFactory` | `com.inductiveautomation.ignition.common.jsonschema` | **ADOPTED (2026-08-30)** — powers `PerspectiveComponentSchemaCatalog` + `PerspectiveViewValidator.checkComponentSchema`. |
| `RefValidator` (used by native `JsonSchema.validate`) | `com.inductiveautomation.ignition.common.jsonschema.validators` | **Not used directly** — bytecode audit (8.3.7) confirmed its `urn:ignition-schema:` branch only tries `jsonSchema.getSubSchema()` (null on factory-built schemas) and **silently skips** the property, logging a warning. The catalog therefore pre-resolves each URN ref into its own standalone `JsonSchema` (which still resolves internal `#/definitions/*` natively) and validates those props separately. |
| `ComponentRegistry` / `ComponentDescriptor.schema()` | `com.inductiveautomation.perspective.common.api` | **Not used** — loads the same native `*.components.json` files and returns a native `JsonSchema`, but exposes no raw schema element (needed to pre-resolve URN refs) and drags in AWT/Swing icon plumbing. The custom catalog reads the identical classpath resources directly. |
| `ResourceValidator` | `com.inductiveautomation.ignition.gateway.config` | **Should investigate** — validates resources and returns `ValidationErrors`. Could provide native resource-level validation (view resource config is not a `props` schema). |
| `Expression` / `ExpressionParseContext` / `ExpressionFunctionManager` | `com.inductiveautomation.ignition.common.expressions` | **ADOPTED (2026-08-29)** — `ELParserHarness.parse(expr, ctx)` + `DefaultFunctionFactory.getSharedInstance()` is the same engine Perspective uses; drives the native grammar validation in `IgnitionExpressionValidator`. `ExpressionFunctionManager` remains internal. |
| `ValidationEngine` / `Validator<T>` | `com.inductiveautomation.ignition.designer.gui.validation` | **DESIGNER-ONLY** — cannot use in Gateway module. |
| `JsonSchemaValidator` / `DocumentValidator` | `com.inductiveautomation.ignition.client.jsonedit` | **DESIGNER/CLIENT-ONLY** — cannot use in Gateway module. |

---

## 4. Immediate Action Items

1. ~~**HIGH** — Replace custom tag validation with `TagPathValidator`~~ ✅ **DONE** — `TagPathValidator` adopted in `DiagnosticService.checkTagPath()` (2026-08-29)
2. ~~**HIGH** — Replace `DiagnosticIssue` with `ValidationErrors` for native error format (evaluate fit for AI-facing format)~~ ✅ **PARTIAL/DECISION (2026-08-29)** — verified native `ValidationErrors` is message/field-only (no `code`, ERROR-vs-WARNING `severity`, `category`, or `suggestions`), which the AI wire contract requires. Adopted as an **adapter** (`ValidationErrorsMapper`) rather than a replacement so the AI-facing format is preserved while gaining native interop (`ValidationErrors.write`, `ValidationException`).
3. ~~**MEDIUM** — Integrate `JsonSchema` for view/component config validation~~ ✅ **DONE (2026-08-30)** — `PerspectiveComponentSchemaCatalog` + `PerspectiveViewValidator.checkComponentSchema`; native `*.components.json` schemas, URN-ref props resolved standalone, binding values exempted from type checks.
4. ~~**MEDIUM** — Use `Expression`/`ExpressionParseContext` for structured expression analysis (if Perspective uses same engine)~~ ✅ **DONE** — `ELParserHarness` + `DefaultFunctionFactory` adopted in `IgnitionExpressionValidator` (2026-08-29); heuristic content checks centralized there and removed from `DiagnosticService`.
5. ~~**LOW** — Investigate `ResourceValidator` for resource-level validation~~ ✅ **DONE (2026-08-30)** — closed: interface-only, no gateway-side registry, Perspective registers no view validator against it; the real path is Designer's client-side `ValidationEngine`.
6. ~~**LOW** — Audit `GatewayIntrospectionService` `TagPathParser.parseSafe` call sites (lines 81, 138) vs the adopted `TagPathValidator`~~ ✅ **DONE (2026-08-30)** — kept: syntax parsing for browse/read, existence is verified by the provider call, not a semantic-validation use.

---

## 5. File Reference Map

| Tracking File | Purpose |
|---|---|
| `native-apis-used.md` | This file — Ignition-native APIs in use |
| `custom-implementations.md` | Features built without native API |
| `investigated-not-adopted.md` | Native APIs considered but not used |