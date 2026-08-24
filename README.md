# AI Agent Tools (Ignition Gateway Module)

Gateway-side REST toolkit that lets AI agents work directly against a running Ignition 8.3 gateway:
validate Perspective view JSON, run headless Jython, browse/read tags, run named queries, and
list project resources.

- Module id: `com.axcend.ignition.agenttools`
- Base URL: `http://localhost:8088/data/agent-tools`
- Target: Ignition 8.3.x (built/pinned against 8.3.7), JDK 17

## Endpoints

| Method | Path | Body | Notes |
|--------|------|------|-------|
| GET  | `/health` | - | Liveness + capability list |
| POST | `/view/validate` | `{viewJson}` or `{viewJsonString}` or `{filePath}` | Structural validation of Perspective view JSON. Supports full view-resource format (`{params, custom, root:{...}}`) and bare component trees. |
| POST | `/script/exec` | `{code, timeoutSec?}` | Headless Jython via gateway ScriptManager. Bind a variable named `result` to return a value; `print()` output is captured in `stdout`. Default timeout 15s, max 120s. Dev-open by design. |
| GET  | `/gateway/info` | - | State, memory, tag providers, project list |
| GET  | `/tags/providers` | - | Tag provider names |
| POST | `/tags/browse` | `{path, recursive?, maxResults?}` | Browse folders/tags under a path (`[default]Machines`) |
| POST | `/tags/read` | `{paths: [...]}` | Read current values of one or more tag paths |
| POST | `/query/run` | `{project?, queryPath, parameters?}` | Execute a stored named query. Datasets come back as `{kind:"dataset", columns, rows}`. |
| GET  | `/projects` | - | Project names |
| POST | `/projects/resources` | `{project, contains?, maxResults?}` | List project resources (views, named queries, scripts...), optionally filtered by path substring |

Read-style routes check gateway session READ permission (anonymous passes on this dev gateway).
`/script/exec` and `/query/run` are intentionally dev-open (no session gate) because agents call
them over plain HTTP; both are logged per call.

### view/validate response shape

```json
{
  "valid": false,
  "errors":   [{"path": "$.root.children[0].type", "code": "MISSING_COMPONENT_TYPE", "severity": "ERROR", "message": "..."}],
  "warnings": [{"path": "$.root.type", "code": "DEPRECATED_ALIAS", "severity": "WARNING", "message": "'ia.text.label' is not current; use 'ia.display.label'."}],
  "stats": {"componentCount": 3, "maxDepth": 2, "bindingCount": 1}
}
```

Error codes include: `MISSING_REQUIRED_KEY`, `MISSING_ROOT_TYPE`, `MISSING_META`,
`MISSING_COMPONENT_NAME`, `MISSING_COMPONENT_TYPE`, `UNKNOWN_COMPONENT_TYPE` (W),
`DEPRECATED_ALIAS` (W), `CHILDREN_NOT_ARRAY`, `CHILD_NOT_OBJECT`, `DUPLICATE_SIBLING_NAME` (W),
`LAYOUT_NOT_OBJECT`, `LAYOUT_NOT_NUMERIC` (W), `FLEX_ZERO_GROW_NO_BASIS` (W),
`STYLE_LAYOUT_KEYS` (W), `BINDING_MISSING_CONFIG`, `TRANSFORMS_NOT_ARRAY`,
`PROPCONFIG_NOT_OBJECT`, `EVENTS_NOT_OBJECT`, `VIEW_KEY_NOT_OBJECT`, `PARSE_ERROR`.

Known real-view fact: Ignition does not persist `meta.id`; it is assigned at runtime, so its
absence is never reported.

## Script exec contract

```json
{"code": "result = 6 * 7\nprint('hello')", "timeoutSec": 10}
```
```json
{"success": true, "result": "42", "resultType": "int", "stdout": "hello\r\n", "stderr": "", "durationMs": 71}
```

Failures return `success:false` with `error` containing the Jython traceback or the timeout
notice; HTTP stays 200 so transport errors are distinguishable from script errors.
Gateway-scope `system.*` functions are available (e.g. `system.tag.browse('[default]')`);
client-scope functions are not.

## Build & deploy

```
.\deploy.ps1              # clean build, register in data\modules.json, copy modl, restart, verify
.\deploy.ps1 -SkipBuild   # redeploy last build
.\gradlew.bat :gateway:test
```

Deploy requires stopping/starting the gateway (`stop-ignition.bat` / `start-ignition.bat`);
Ignition 8.3 ignores dropped modl files unless registered in `data\modules.json`, which the
script handles (with retry for post-shutdown file locks). The `clean` before build is required -
the modl plugin can otherwise re-package stale `build/moduleContent`.

## Layout

```
gateway/src/main/java/com/axcend/ignition/agenttools/
  AgentToolsHook.java               hook: mounts all routes under /data/agent-tools
  AgentToolsRouteHandlers.java      HTTP handlers + permission checks
  GatewayScriptService.java         Jython exec with stdout capture + timeout
  GatewayIntrospectionService.java  tags / queries / projects / info
  validate/
    ComponentCatalog.java           known ia.* component ids + aliases
    PerspectiveViewValidator.java   structural rules + ValidationResult
    ValidationIssue.java            issue record
gateway/src/test/java/...          validator unit tests
```
