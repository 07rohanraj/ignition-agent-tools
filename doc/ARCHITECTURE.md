# AI Agent Tools — Architecture & Reference

> Gateway-side REST toolkit for AI agents working on Ignition 8.3 Perspective projects.

| Attribute | Value |
|-----------|-------|
| Module ID | `com.axcend.ignition.agenttools` |
| Name | AI Agent Tools |
| Vendor | Axcend |
| Version | `0.1.0-SNAPSHOT` |
| Required Ignition | 8.3.7 |
| JDK | 17 |
| Base URL | `http://localhost:8088/data/agent-tools` |
| Gateway Hook | `com.axcend.ignition.agenttools.AgentToolsHook` |

---

## Table of Contents

1. [Source Layout](#1-source-layout)
2. [Endpoints](#2-endpoints)
3. [Validation System](#3-validation-system)
4. [Important Functions](#4-important-functions)
5. [Service Layer](#5-service-layer)
6. [Security Model](#6-security-model)
7. [Error Code Reference](#7-error-code-reference)
8. [Build & Deploy](#8-build--deploy)

> See also: [Native Component Schema Validation](NATIVE-COMPONENT-SCHEMA-VALIDATION.md) — plain-
> language summary of the native `props` schema validation (what was done, which native functions,
> what it enables).

---

## 1. Source Layout

```
gateway/src/main/java/com/axcend/ignition/agenttools/
│
├── AgentToolsHook.java                  (115 lines)  GatewayHook — mounts all 10 routes
├── AgentToolsRouteHandlers.java         (313 lines)  HTTP handlers + permission checks
├── GatewayScriptService.java            (125 lines)  Headless Jython execution via ScriptManager
├── GatewayIntrospectionService.java     (379 lines)  Tags / queries / projects / gateway info
│
└── validate/
    ├── ComponentCatalog.java            (74 lines)   Static catalog of known ia.* component IDs
    ├── PerspectiveViewValidator.java    (526 lines)  Pure-logic structural + native-schema validator
    ├── PerspectiveComponentSchemaCatalog.java (230 lines)  Native component props schemas from the perspective-common classpath
    └── ValidationIssue.java             (19 lines)   Issue record {path, code, severity, message}

gateway/src/test/java/com/axcend/ignition/agenttools/
│
├── PerspectiveViewValidatorTest.java    (675 lines)  ~40 tests covering all validation rules
├── PerspectiveComponentSchemaCatalogTest.java (94 lines)  Native schema catalog tests
└── ComponentCatalogTest.java            (61 lines)   Catalog integrity tests
```

---

## 2. Endpoints

All routes mounted under `/main/agent-tools`. All requests/responses are JSON.

| # | Method | Path | Auth | Handler | Description |
|---|--------|------|------|---------|-------------|
| 1 | GET | `/health` | Open | `health()` | Liveness + capability manifest |
| 2 | POST | `/view/validate` | READ | `validateView()` | Structural validation of Perspective view JSON |
| 3 | POST | `/script/exec` | Dev-open | `execScript()` | Headless Jython execution |
| 4 | GET | `/gateway/info` | READ | `gatewayInfo()` | Gateway state, memory, modules, providers |
| 5 | GET | `/tags/providers` | READ | `tagProviders()` | Tag provider name list |
| 6 | POST | `/tags/browse` | READ | `browseTags()` | Browse tag tree under a path |
| 7 | POST | `/tags/read` | READ | `readTags()` | Read current values of tag paths |
| 8 | POST | `/query/run` | Dev-open | `runQuery()` | Execute a stored named query |
| 9 | GET | `/projects` | READ | `listProjects()` | Project name list |
| 10 | POST | `/projects/resources` | READ | `projectResources()` | List project resources with filtering |

### 2.1 `GET /health`

```json
{
  "status": "ok",
  "module": "com.axcend.ignition.agenttools",
  "version": "0.1.0",
  "capabilities": [
    "view.validate", "script.exec", "gateway.info",
    "tags.browse", "tags.read", "query.run", "projects.resources"
  ]
}
```

### 2.2 `POST /view/validate`

Accepts three input forms (pick one):

| Field | Type | Description |
|-------|------|-------------|
| `viewJson` | Object | Inline JSON (preferred) |
| `viewJsonString` | String | Escaped JSON string |
| `filePath` | String | Gateway-local file path |

**Request:**
```json
{
  "viewJson": {
    "meta": {},
    "props": {
      "direction": "row",
      "wrap": "nowrap",
      "justify": "flex-start",
      "alignItems": "stretch",
      "alignContent": "stretch",
      "style": {}
    },
    "type": "ia.container.flex",
    "children": []
  }
}
```

**Response:**
```json
{
  "valid": false,
  "errors": [
    {
      "path": "$.root.children[0].type",
      "code": "MISSING_COMPONENT_TYPE",
      "severity": "ERROR",
      "message": "Component 'type' is missing or blank."
    }
  ],
  "warnings": [
    {
      "path": "$.root.type",
      "code": "DEPRECATED_ALIAS",
      "severity": "WARNING",
      "message": "'ia.text.label' is not a current component ID; use 'ia.display.label'."
    }
  ],
  "stats": {
    "componentCount": 3,
    "maxDepth": 2,
    "bindingCount": 1
  }
}
```

See [Section 7: Error Code Reference](#7-error-code-reference) for all codes.

### 2.3 `POST /script/exec`

**Request:**
```json
{
  "code": "result = system.tag.readBlocking(['[default]Motor/Speed'])[0].value\nprint('Speed:', result)",
  "timeoutSec": 10
}
```

**Response:**
```json
{
  "success": true,
  "result": "1750",
  "resultType": "int",
  "stdout": "Speed: 1750\r\n",
  "stderr": "",
  "durationMs": 71
}
```

- Default timeout: 15s, max: 120s
- Bind a variable named `result` to return a value
- `system.*` gateway-scope functions are available
- HTTP always 200; check `success` in payload
- Code max: 100,000 characters

### 2.4 `POST /tags/browse`

**Request:**
```json
{
  "path": "[default]Area1",
  "recursive": false,
  "maxResults": 200
}
```

**Response:**
```json
{
  "path": "[default]Area1",
  "count": 2,
  "nodes": [
    {
      "name": "Motor1",
      "path": "[default]Area1/Motor1",
      "type": "Folder",
      "hasChildren": true,
      "dataType": null,
      "currentValue": null,
      "quality": null
    },
    {
      "name": "Speed",
      "path": "[default]Area1/Motor1/Speed",
      "type": "AtomicTag",
      "hasChildren": false,
      "dataType": "Int4",
      "currentValue": 1750,
      "quality": "Good"
    }
  ]
}
```

- `maxResults` clamped 1–1000 (default 200)
- 10s timeout on browse future

### 2.5 `POST /tags/read`

**Request:**
```json
{
  "paths": [
    "[default]Area1/Motor1/Speed",
    "[default]Area1/Motor1/Status"
  ]
}
```

**Response:**
```json
{
  "values": [
    {
      "path": "[default]Area1/Motor1/Speed",
      "value": 1750,
      "quality": "Good",
      "timestamp": "2026-08-24T14:30:00.000+00:00"
    },
    {
      "path": "[default]Area1/Motor1/Status",
      "value": true,
      "quality": "Good",
      "timestamp": "2026-08-24T14:30:00.000+00:00"
    }
  ],
  "invalidPaths": []
}
```

- Invalid/unparseable paths go into `invalidPaths`, not fatal

### 2.6 `POST /query/run`

**Request:**
```json
{
  "project": "MyProject",
  "queryPath": "Queries/GetAlarms",
  "parameters": {
    "area": "Area1"
  }
}
```

**Response:**
```json
{
  "success": true,
  "queryPath": "Queries/GetAlarms",
  "project": "MyProject",
  "result": {
    "kind": "dataset",
    "columns": ["alarmId", "message", "timestamp"],
    "rowCount": 2,
    "rows": [
      ["a1", "High Temp", "2026-08-24T10:00:00.000+00:00"],
      ["a2", "Low Pressure", "2026-08-24T10:05:00.000+00:00"]
    ]
  }
}
```

### 2.7 `GET /projects`

```json
{
  "count": 2,
  "projects": ["ProjectA", "ProjectB"]
}
```

### 2.8 `POST /projects/resources`

**Request:**
```json
{
  "project": "MyProject",
  "contains": "Motor",
  "maxResults": 50
}
```

**Response:**
```json
{
  "project": "MyProject",
  "count": 3,
  "truncated": false,
  "resources": [
    {
      "path": "perspective/views/Motor/MotorOverview",
      "name": "MotorOverview",
      "folder": "Motor",
      "type": "view",
      "dataKeys": ["viewPath", "params"],
      "documentation": "Motor overview dashboard..."
    }
  ]
}
```

---

## 3. Validation System

### 3.1 How Validation Works

**Validation is hybrid: hand-written structural rules + Ignition's native JSON Schema engine.**

`PerspectiveViewValidator` operates on parsed Gson JSON trees and requires no gateway services, no
running Perspective instance, and… no live component registry. **Since 2026-08-30** it additionally
validates every known component's `props` object against the **native Perspective component schema**
packaged in `perspective-common`'s descriptor files (`*.components.json`), compiled with Ignition's
own `JsonSchema`/`JsonSchemaFactory`. See §3.2b.

This design was deliberate:
- Fully unit-testable without a running gateway (all tests run offline — the descriptor files are on
  the module classpath)
- No dependency on live Perspective internals
- Fast — no round-trips to gateway services
- Native fidelity — prop types, enums, required keys, and `additionalProperties` restrictions come
  straight from the schemas the Designer itself uses

### 3.2 What ComponentCatalog Does

`ComponentCatalog.java` is a **hand-curated static list** of known stock Perspective component type IDs. It serves three purposes:

| Field | Purpose | Used By |
|-------|---------|---------|
| `KNOWN_TYPES` | ~70 stock `ia.*` component type IDs | `checkComponentType()` — warns on unknown `ia.*` types |
| `ALIASES` | 4 deprecated→canonical mappings | `checkComponentType()` — warns on deprecated usage |
| `FLEX_STYLE_KEYS` | CSS keys that belong on flex props | `checkStylePlacement()` — warns on wrong placement |
| `BINDING_TYPES` | 8 recognized binding type discriminators | `checkBindings()` — shape-detection of binding objects |

**Critical design decision:** Unknown `ia.*` types produce **warnings**, not errors. The catalog can lag behind new Ignition versions, and third-party modules introduce custom types. This is documented in `ComponentCatalog` javadoc and tested in `PerspectiveViewValidatorTest`.

Non-`ia.` types (third-party module components) pass silently with no warning.

### 3.2b What PerspectiveComponentSchemaCatalog Does

`PerspectiveComponentSchemaCatalog.java` loads the exact descriptor files Perspective's real
`ComponentRegistry` reads (`ia.components.json`, `barcode.component.json`,
`perspective-timeseries.components.json`, `perspective-googlemap.components.json`,
`perspective-amcharts.components.json`, `perspective-map.components.json`,
`pdf-viewer.components.json`) from the classpath, and compiles each component's `props` schema into a
native `JsonSchema` via `JsonSchemaFactory`.

- `find(typeId)` → `Optional<ComponentSchema>` (raw schema element + compiled `JsonSchema` +
  resolved URN-ref props)
- When a component's `props` object exists, `checkComponentSchema()` validates it with the native
  engine → issues coded `SCHEMA_*` (e.g. `SCHEMA_REQUIRED`, `SCHEMA_ENUM`, `SCHEMA_TYPE`,
  `SCHEMA_ADDITIONALPROPERTIES`) at ERROR severity.
- **URN `$ref` props** (`style`, `textStyle`, …) are pre-resolved into standalone native schemas
  because the native `RefValidator` silently skips `urn:ignition-schema:` refs on factory-built
  schemas (bytecode-verified in `common-8.3.7.jar`).
- **Binding-valued props** (e.g. `{"type": "expr", "config": {...}}`) are exempted from type/enum
  checks — a binding object is not a literal of the prop's declared type, so it would false-fail
  strictly-typed props like `text-field.text`.
- **Graceful degradation:** if the descriptor resources are absent, the catalog is empty and the
  validator silently falls back to structural checks only (no crashes, no spurious errors).

### 3.3 Validation Flow

```
POST /view/validate
       │
       ▼
AgentToolsRouteHandlers.validateView()
       │
       ├── Parse body (viewJson / viewJsonString / filePath)
       ├── Handle JsonParseException → PARSE_ERROR
       │
       ▼
PerspectiveViewValidator.validate(JsonElement)
       │
       ├── Null/empty → EMPTY_DOCUMENT (ERROR)
       ├── Non-object → ROOT_NOT_OBJECT (ERROR)
       │
       ▼
Format auto-detection
       │
       ├── Has "root" key (object) → Full view resource format
       │     ├── Validate wrapper keys (params/custom/propConfig/events)
       │     ├── Check root.type exists
       │     └── Walk $.root
       │
       └── No "root" key → Bare component tree
             ├── Require meta (object), props (object), type (non-blank)
             └── Walk $
       │
       ▼
Recursive walk() per node (capped at 200 issues)
       │
       ├── checkComponentType()  → DEPRECATED_ALIAS, UNKNOWN_COMPONENT_TYPE, SUSPICIOUS_TYPE_FORMAT
       ├── checkComponentSchema()→ SCHEMA_REQUIRED, SCHEMA_ENUM, SCHEMA_TYPE, SCHEMA_ADDITIONALPROPERTIES, … (native, when the component's props schema is on the classpath)
       ├── checkMeta()           → MISSING_META, META_NOT_OBJECT, MISSING_COMPONENT_NAME
       ├── checkChildren()       → CHILDREN_NOT_ARRAY, CHILD_NOT_OBJECT, DUPLICATE_SIBLING_NAME
       ├── checkFlexLayout()     → LAYOUT_NOT_OBJECT, LAYOUT_NOT_NUMERIC, FLEX_NEGATIVE_GROW, FLEX_ZERO_GROW_NO_BASIS
       ├── checkStylePlacement() → STYLE_LAYOUT_KEYS
       ├── checkBindings()       → BINDING_MISSING_CONFIG, TRANSFORMS_NOT_ARRAY
       └── checkPropConfigAndEvents() → PROPCONFIG_NOT_OBJECT, PROPCONFIG_ENTRY_NOT_OBJECT, EVENTS_NOT_OBJECT, EVENT_ENTRY_NOT_OBJECT
       │
       ▼
ValidationResult
       ├── valid() == true if zero errors
       ├── errors[] (severity ERROR)
       ├── warnings[] (severity WARNING)
       └── stats { componentCount, maxDepth, bindingCount }
```

### 3.4 Two Supported Input Formats

**Full view resource** (what Ignition stores in `view.json`):
```json
{
  "params": {},
  "custom": {},
  "propConfig": {},
  "events": {},
  "root": {
    "type": "ia.container.flex",
    "meta": {"name": "root"},
    "props": {
      "direction": "row",
      "wrap": "nowrap",
      "justify": "flex-start",
      "alignItems": "stretch",
      "alignContent": "stretch",
      "style": {}
    },
    "children": []
  }
}
```

**Bare component tree** (just the component):
```json
{
  "type": "ia.container.flex",
  "meta": {"name": "root"},
  "props": {
    "direction": "row",
    "wrap": "nowrap",
    "justify": "flex-start",
    "alignItems": "stretch",
    "alignContent": "stretch",
    "style": {}
  },
  "children": []
}
```

The validator auto-detects which format by checking for a `root` key that is an object.

> Note: the examples use schema-conformant flex `props` — since 2026-08-30 a flex with bare
> `props: {}` is reported (`SCHEMA_REQUIRED`), matching the Designer's own schema, which requires
> `direction`, `wrap`, `justify`, `alignItems`, `alignContent`, and `style`.

### 3.5 meta.id Is Never Checked

Ignition does not persist `meta.id` in `view.json` — the Designer assigns it at runtime. Its absence is never reported. This is verified in `PerspectiveViewValidatorTest.missingMetaIdDoesNotWarn()`.

---

## 4. Important Functions

### 4.1 Validation

#### `PerspectiveViewValidator.validate(JsonElement root)` → `ValidationResult`

Entry point. Accepts a parsed JSON element (full view resource or bare component tree). Returns `ValidationResult` with `valid()`, `errors()`, `warnings()`, `stats()`. Never throws on malformed input.

#### `PerspectiveViewValidator.walk(JsonObject node, String path, int depth, ...)`

Recursive node walker. For each component node, runs all structural checks (type, meta, children, layout, style, bindings, propConfig, events) **and**, when the component's props schema is available, the native schema check (see §3.2b). Adds JSON-path-style location to every issue (e.g., `$.root.children[0].props.text`). Stops at `MAX_ISSUES = 200`.

#### `PerspectiveComponentSchemaCatalog.find(String typeId)` → `Optional<ComponentSchema>`

Looks up the native `props` schema for a component id (aliases resolved via `ComponentCatalog.canonicalFor` first). Returns the raw schema element, the compiled native `JsonSchema`, and any URN-ref props resolved as standalone schemas. Empty when the component is unknown or the descriptor resources are absent.

#### `ComponentCatalog.isKnown(String type)` → `boolean`

Checks if a component type ID is in the static `KNOWN_TYPES` set. Used to distinguish unknown stock components (warning) from third-party types (silent).

#### `ComponentCatalog.canonicalFor(String type)` → `Optional<String>`

Looks up deprecated type IDs in the `ALIASES` map. Returns the canonical replacement if found.

#### `ValidationResult.toMap()` → `Map<String, Object>`

Serializes the validation result into the documented wire format: `{valid, errors[], warnings[], stats{componentCount, maxDepth, bindingCount}}`.

### 4.2 Script Execution

#### `GatewayScriptService.exec(JsonObject request)` → `Map<String, Object>`

Executes Jython code through the gateway's `ScriptManager`. Runs on a dedicated daemon thread (`agent-tools-script-exec`). Captures stdout/stderr. Returns optional `result` variable as string + type. Timeout clamped 1–120s (default 15s). Code max 100,000 chars.

Key flow:
1. Create fresh `locals` namespace via `ScriptManager.createLocalsMap()`
2. Attach stdout/stderr capture streams
3. Submit code to executor, await with timeout
4. Read `result` from locals if present
5. Always return HTTP 200; check `success` in payload

### 4.3 Gateway Introspection

#### `GatewayIntrospectionService.info()` → `Map<String, Object>`

Returns gateway state, Java version, heap memory (used/total MB), module info, tag provider names, project names.

#### `GatewayIntrospectionService.browseTags(String path, boolean recursive, Integer maxResults)` → `Map<String, Object>`

Parses tag path via `TagPathParser.parseSafe()`, resolves `TagProvider`, browses with `BrowseFilter`. Returns nodes with `name`, `path`, `type`, `hasChildren`, `dataType`, `currentValue`, `quality`. 10s timeout.

#### `GatewayIntrospectionService.readTags(List<String> paths)` → `Map<String, Object>`

Batch read via `TagProvider.readAsync()`. Returns `values[]` with `path`, `value`, `quality`, `timestamp` (ISO-8601). Invalid paths collected in `invalidPaths[]` — not fatal.

#### `GatewayIntrospectionService.runNamedQuery(String project, String queryPath, JsonObject parameters)` → `Map<String, Object>`

Executes via `NamedQueryManager.execute()` with `canCache=false`, `canLimit=true`. Dataset results converted to `{kind:"dataset", columns[], rowCount, rows[][]}`. Errors caught and returned as `success:false` payloads.

#### `GatewayIntrospectionService.listResources(String project, String containsFilter, Integer maxResults)` → `Map<String, Object>`

Iterates `RuntimeResourceCollection.getAllResources()`, skips folders, applies substring filter. Returns `resources[]` with `path`, `name`, `folder`, `type`, `dataKeys`, `documentation` (truncated to 300 chars). Sets `truncated: true` if results exceed `maxResults`.

### 4.4 Route Handling

#### `AgentToolsRouteHandlers.validateView(RequestContext, HttpServletResponse)` → `Object`

Parses request body, supports three input forms (`viewJson`, `viewJsonString`, `filePath`). Delegates to `PerspectiveViewValidator.validate()`. Handles `JsonParseException` → structured `PARSE_ERROR` payload.

#### `AgentToolsRouteHandlers.requireGatewayPermission(...)` → `Object or null`

Maps `PermissionType.READ/WRITE/ACCESS` to `WebUiSession.SESSION_*` checks. Returns `null` if granted (proceed), or JSON error object with 401/403 status.

#### `AgentToolsHook.mountRouteHandlers(RouteGroup routes)`

Registers all 10 routes with the Ignition dataroutes framework. Mount path alias: `agent-tools`. All routes use `TYPE_JSON` and `AccessControlStrategy.OPEN_ROUTE` (per-handler auth enforced inside handlers).

---

## 5. Service Layer

### 5.1 GatewayScriptService

| Aspect | Detail |
|--------|--------|
| Execution | Gateway `ScriptManager.runCode()` on dedicated daemon thread |
| Thread name | `agent-tools-script-exec` |
| Namespace | Fresh `locals` per call via `createLocalsMap()` — no cross-call state |
| Stdout/Stderr | Captured via `addStdOutStream`/`addStdErrStream`, removed in `finally` |
| Timeout | Default 15s, clamped 1–120s |
| Code limit | 100,000 characters |
| Result | Optional `result` variable from locals, returned as string + Jython type |
| Error handling | Timeout → cancelled future; ExecutionException → root cause walk |
| Always HTTP 200 | Script failure ≠ transport failure; check `success` field |

### 5.2 GatewayIntrospectionService

| Aspect | Detail |
|--------|--------|
| Tag browse | `TagProvider.browseAsync()` with `BrowseFilter(recursive, maxResults)`, 10s timeout |
| Tag read | `TagProvider.readAsync()` batch read, invalid paths reported per-entry |
| Named queries | `NamedQueryManager.execute()` fresh execution, datasets → columnar JSON |
| Resources | `RuntimeResourceCollection.getAllResources()` with substring filter |
| Value serialization | Dates → ISO-8601, byte[] → `"<n bytes>"`, arrays/iterables recursed |
| Max results | Default 200, hard max 1000 |

---

## 6. Security Model

| Route | Auth | Notes |
|-------|------|-------|
| `/health` | None | Open |
| `/view/validate` | `SESSION_READ` | Requires gateway session |
| `/script/exec` | **None** | Dev-open by design (agents call over plain HTTP) |
| `/gateway/info` | `SESSION_READ` | |
| `/tags/providers` | `SESSION_READ` | |
| `/tags/browse` | `SESSION_READ` | |
| `/tags/read` | `SESSION_READ` | |
| `/query/run` | **None** | Dev-open by design (logged per call) |
| `/projects` | `SESSION_READ` | |
| `/projects/resources` | `SESSION_READ` | |

- Routes registered with `AccessControlStrategy.OPEN_ROUTE`
- Per-handler permission checks via `WebUiSession.SESSION_*.canAccess()`
- `/script/exec` and `/query/run` are intentionally ungated — agents call them without a web session; both are logged per call
- No tag writes or resource mutations in v1 by design

### Future Hardening (post-v1)

- Config-gated enable flag
- Shared-secret header
- IP allow-list

---

## 7. Error Code Reference

### Validation Error Codes (`/view/validate`)

| Code | Severity | When Emitted | Fix |
|------|----------|--------------|-----|
| `EMPTY_DOCUMENT` | ERROR | Root is null or JSON null | Provide a valid JSON object |
| `ROOT_NOT_OBJECT` | ERROR | Root is not a JSON object | Root must be `{}` |
| `MISSING_REQUIRED_KEY` | ERROR | `meta` or `props` missing from root | Add the missing key as a JSON object |
| `NOT_AN_OBJECT` | ERROR | `meta` or `props` exists but is not an object | Change to `{}` |
| `MISSING_ROOT_TYPE` | ERROR | Root (or `$.root`) has no `type` | Add `"type": "ia.container.flex"` (or appropriate) |
| `MISSING_COMPONENT_TYPE` | ERROR | Any node has blank/missing `type` | Add a non-blank `type` string |
| `SCHEMA_REQUIRED` | ERROR | A `props` object is missing a prop the component's native schema requires (e.g. flex requires `direction`/`wrap`/`justify`/`alignItems`/`alignContent`/`style`) | Add the required prop(s) per the component's schema |
| `SCHEMA_TYPE` | ERROR | A `props` value has the wrong native type (e.g. `text-field.text` must be a string) | Fix the value's type |
| `SCHEMA_ENUM` | ERROR | A `props` value is not in the component's allowed enum | Use an allowed value |
| `SCHEMA_ADDITIONALPROPERTIES` | ERROR | A `props` key is not in the component schema and `additionalProperties` is false | Remove the unknown key |
| `SCHEMA_*` (others) | ERROR | Any other native schema violation (format, pattern, min/max, …) | Follow the message text |
| `MISSING_META` | ERROR | Node has no `meta` object | Add `"meta": {"name": "componentName"}` |
| `META_NOT_OBJECT` | ERROR | `meta` exists but is not an object | Change to `{}` |
| `MISSING_COMPONENT_NAME` | ERROR | `meta.name` is missing or blank | Add `"name": "uniqueName"` |
| `CHILDREN_NOT_ARRAY` | ERROR | `children` exists but is not an array | Change to `[]` |
| `CHILD_NOT_OBJECT` | ERROR | An entry in `children` is not an object | Each child must be `{}` |
| `LAYOUT_NOT_OBJECT` | ERROR | `layout` exists but is not an object | Change to `{}` |
| `BINDING_MISSING_CONFIG` | ERROR | Binding detected (has `type` in BINDING_TYPES) but no `config` object | Add `"config": {}` |
| `TRANSFORMS_NOT_ARRAY` | ERROR | Binding has `transforms` but it's not an array | Change to `[]` |
| `PROPCONFIG_NOT_OBJECT` | ERROR | `propConfig` exists but is not an object | Change to `{}` |
| `EVENTS_NOT_OBJECT` | ERROR | `events` exists but is not an object | Change to `{}` |
| `VIEW_KEY_NOT_OBJECT` | ERROR | `params`/`custom`/`propConfig`/`events` exists but is not an object | Change to `{}` |
| `PARSE_ERROR` | ERROR | JSON parse failure | Fix the JSON syntax |
| `TOO_MANY_ISSUES` | WARNING | Validation stopped at 200 issues | Fix the errors above; re-validate |

### Validation Warning Codes

| Code | Severity | When Emitted | Fix |
|------|----------|--------------|-----|
| `DEPRECATED_ALIAS` | WARNING | Type is in `ComponentCatalog.ALIASES` | Use the canonical type ID |
| `UNKNOWN_COMPONENT_TYPE` | WARNING | `ia.*` type not in `KNOWN_TYPES` | Verify the type ID exists in your Ignition version |
| `SUSPICIOUS_TYPE_FORMAT` | WARNING | Type doesn't contain a dot | Use format `ia.display.label` |
| `DUPLICATE_SIBLING_NAME` | WARNING | Siblings share the same `meta.name` | Give each sibling a unique name |
| `LAYOUT_NOT_NUMERIC` | WARNING | `grow`/`shrink` is not a number | Use numeric values |
| `FLEX_NEGATIVE_GROW` | WARNING | `grow` is negative | Use `grow: 0` or higher |
| `FLEX_ZERO_GROW_NO_BASIS` | WARNING | `grow=0` with no `basis` | Component may collapse; add a `basis` value |
| `STYLE_LAYOUT_KEYS` | WARNING | Flex CSS keys in `props.style` | Move to `props.direction`/`justify`/`alignItems`/`wrap` |
| `PROPCONFIG_ENTRY_NOT_OBJECT` | WARNING | `propConfig` entry is not an object | Change to `{type, config}` |
| `EVENT_ENTRY_NOT_OBJECT` | WARNING | Event entry is not an object | Change to `{}` |

### Service Error Codes

| Endpoint | Error | Meaning |
|----------|-------|---------|
| `/script/exec` | `success: false` + `error` | Script error, timeout, or interruption |
| `/query/run` | `success: false` + `error` | Named query execution failed |
| `/tags/read` | `invalidPaths[]` | Unparseable tag paths (per-entry, not fatal) |

### Diagnostic Error Codes (`/diagnostics/*`)

| Code | Severity | Category | When Emitted | Fix |
|------|----------|----------|--------------|-----|
| `VIEW_NOT_FOUND` | ERROR | VIEW | View not found in project | Verify view path exists |
| `INVALID_VIEW_DOCUMENT` | ERROR | VIEW | View JSON is not a valid Perspective document | Fix view JSON structure |
| `MISSING_ROOT_COMPONENT` | ERROR | VIEW | View has no root component | Add root component |
| `MISSING_COMPONENT_TYPE` | ERROR | COMPONENT | Component has no `type` field | Add component type |
| `DEPRECATED_TYPE_ALIAS` | WARNING | COMPONENT | Component type is a deprecated alias | Use canonical type ID |
| `UNKNOWN_COMPONENT_TYPE` | WARNING | COMPONENT | Component type not in standard catalog | Verify type ID |
| `EMPTY_TAG_PATH` | ERROR | BINDING | Tag binding has empty `tagPath` | Provide tag path |
| `MISSING_BINDING_TYPE` | ERROR | BINDING | Binding has no `type` field | Add binding type |
| `MISSING_BINDING_CONFIG` | ERROR | BINDING | Binding has no `config` object | Add config object |
| `INVALID_TAG_PATH` | ERROR | BINDING | Tag path format is invalid | Fix tag path format |
| `TAG_PROVIDER_NOT_FOUND` | ERROR | BINDING | Tag provider not found | Verify tag provider exists |
| `TAG_NOT_FOUND` | ERROR | BINDING | Tag not found at path | Verify tag exists |
| `TAG_QUALITY_NOT_GOOD` | WARNING | BINDING | Tag exists but quality not Good | Check tag quality |
| `TAG_VALIDATION_ERROR` | WARNING | BINDING | Error during tag validation | Check gateway connection |
| `QUERY_NOT_FOUND` | ERROR | BINDING | Named query not found | Verify query exists |
| `INVALID_QUERY_PARAMETERS` | ERROR | BINDING | Query parameters not an object | Fix parameters format |
| `INVALID_POLL_RATE` | ERROR | BINDING | pollRate not a valid number | Fix pollRate value |
| `MISSING_EXPRESSION` | ERROR | BINDING | Expression binding has no expression | Add expression |
| `EXPRESSION_SYNTAX_ERROR` | ERROR | BINDING | Unmatched parentheses in expression | Fix expression syntax |
| `PYTHON_IMPORT_IN_EXPRESSION` | ERROR | BINDING | Python import in expression | Use runScript() instead |
| `CLIENT_SCOPE_FUNCTION_IN_EXPRESSION` | WARNING | BINDING | Client-scope function in gateway scope | Use gateway-scope functions |
| `INVALID_RUNSCRIPT_PATH` | WARNING | BINDING | runScript path not dot-separated | Fix module path format |
| `NEGATIVE_RUNSCRIPT_POLL_RATE` | WARNING | BINDING | runScript pollRate negative | Use non-negative value |
| `INVALID_RUNSCRIPT_POLL_RATE` | WARNING | BINDING | runScript pollRate not a number | Fix pollRate value |
| `MISSING_PROPERTY_PATH` | ERROR | BINDING | Property binding has no path | Add property path |
| `INVALID_PROPERTY_PATH` | ERROR | BINDING | Invalid property path format | Fix path format |
| `UNKNOWN_BINDING_TYPE` | WARNING | BINDING | Binding type not recognized | Use known binding type |
| `COMPONENT_NOT_FOUND` | ERROR | COMPONENT | Component not found at path | Verify component path |
| `DIAGNOSTIC_ERROR` | ERROR | STRUCTURE | Internal diagnostic error | Check logs |

---

## 8. Build & Deploy

```powershell
# Full build + deploy
.\deploy.ps1

# Redeploy last build (skip recompilation)
.\deploy.ps1 -SkipBuild

# Run tests only
.\gradlew.bat :gateway:test
```

### Build Artifacts

- Output: `build/ai-agent-tools.unsigned.modl`
- Registered in: `data\modules.json`
- Deployed to: `user-lib\modules\`
- Gateway restart required after deploy

### Log Check

After every deploy, check:
```
C:\Program Files\Inductive Automation\Ignition\logs\wrapper.log
```

---

*This document covers the `ignition-agent-tools` module (gateway-side REST toolkit). For the AI skill system that guides agents on how to use these endpoints, see `.agents/skills/agent-tools/SKILL.md`.*
