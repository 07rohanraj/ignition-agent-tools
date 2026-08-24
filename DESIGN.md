# AI Agent Tools — Ignition Module Design & Implementation Plan

> Status: **Approved plan (v1)** · Target platform: **Ignition 8.3.x** · Scope: **Gateway only**
> Module id: `com.axcend.ignition.agenttools` · Folder: `ignition-agent-tools/`

---

## 1. Purpose

This workspace (`data\projects`) is developed by an AI agent (opencode) that writes
Perspective view JSON, named queries, scripts, and tag configs directly into project
folders. Today the agent has **no runtime feedback loop**: it cannot validate the JSON it
produces against a live gateway, cannot execute a script to test an idea, and cannot check
whether a tag or query it references actually exists.

**AI Agent Tools** is a small gateway module that closes that loop by exposing a set of
JSON REST endpoints on the local gateway (`http://localhost:8088/agent-tools/*`). The agent
calls them from PowerShell (`Invoke-RestMethod`) exactly like any other tool.

### v1 goals

| # | Capability | Endpoint |
|---|------------|----------|
| 1 | Validate AI-generated Perspective view JSON | `POST /agent-tools/view/validate` |
| 2 | Headless script console (run Jython in gateway scope) | `POST /agent-tools/script/exec` |
| 3 | Gateway ground truth (version, modules, providers, projects) | `GET /agent-tools/gateway/info` |
| 4 | Tag browse + read (verify referenced tags exist) | `POST /agent-tools/tags/browse`, `POST /agent-tools/tags/read` |
| 5 | Named query runner (test queries the agent writes) | `POST /agent-tools/query/run` |
| 6 | Project resource listing (what views/resources exist server-side) | `GET /agent-tools/projects/{name}/resources` |
| — | Liveness + capability manifest | `GET /agent-tools/health` |

### Non-goals for v1

- No Designer UI / dockable panels (the agent talks HTTP; humans use the existing
  AI Assistant panel from `ignition-copilot-designer`).
- No LLM integration — this module is a *tool provider*, not a chat orchestrator.
- No tag writes or config mutations (read-only except script exec, which is explicitly gated).

---

## 2. Reference: how `ignition-copilot-designer` does it

The existing module in this workspace is a working, deployed Ignition 8.3 SDK project.
We reuse its proven patterns rather than inventing new ones. Key facts verified from its source:

### 2.1 Build system

- Gradle multi-module build using the official plugin: `id 'io.ia.sdk.modl' version '0.1.1'`.
- JDK 17 toolchain (`org.gradle.toolchains.foojay-resolver-convention` v0.8.0 auto-provisions it).
- Repositories: `https://nexus.inductiveautomation.com/repository/public` (SDK artifacts)
  + Maven Central, declared in both `pluginManagement` and `dependencyResolutionManagement`
  with `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.
- SDK dependencies are `compileOnly` — Ignition provides them at runtime:

  ```groovy
  compileOnly "com.inductiveautomation.ignitionsdk:gateway-api:${rootProject.ext.ignitionVersion}"
  compileOnly "com.inductiveautomation.ignitionsdk:ignition-common:${rootProject.ext.ignitionVersion}"
  ```

- The `.modl` assembly is driven by the `ignitionModule { }` block:

  ```groovy
  ignitionModule {
      name                   = '...'
      fileName               = '...'          // .modl file name
      id                     = 'com.leptons.ignition.copilot'
      moduleVersion          = project.version.toString()
      requiredIgnitionVersion= '8.3.0'
      projectScopes          = [':common': 'DG', ':gateway': 'G', ':designer': 'D']
      hooks                  = ['...GatewayHook': 'G', '...DesignerHook': 'D']
      skipModlSigning        = true           // dev builds install on maker/dev gateways
      metaInfo.put('javaVersion', '17')
  }
  ```

- Tests run on JUnit 5 (`useJUnitPlatform()`); pure-logic classes are tested without a gateway.

### 2.2 Gateway hook lifecycle

`AiGatewayHook extends AbstractGatewayModuleHook` implements:

| Method | What copilot does there | What we will do |
|--------|------------------------|-----------------|
| `setup(GatewayContext)` | Construct all services, register Gateway Network UI pages | Construct services only |
| `startup(LicenseState)` | no-op | no-op |
| `shutdown()` | null out services | null out services (+ shut down script executor pool) |
| `mountRouteHandlers(RouteGroup)` | Register every REST route | Same pattern, under `/agent-tools/*` |

### 2.3 REST route pattern

Routes are registered declaratively and handlers are plain method references:

```java
routes.newRoute("/health")
      .type(RouteGroup.TYPE_JSON)
      .handler(aiRouteHandlers::health)
      .method(HttpMethod.GET)
      .accessControl(AccessControlStrategy.OPEN_ROUTE)
      .mount();
```

Handler signature — `(RequestContext, HttpServletResponse) -> Object`; returning a POJO/Map
is serialized as JSON by the framework:

```java
public Object upsertPreset(RequestContext requestContext, HttpServletResponse response) {
    Object denial = requireGatewayPermission(requestContext, response, PermissionType.WRITE);
    if (denial != null) return denial;
    try {
        ProjectPreset payload = gson.fromJson(requestContext.readBody(), ProjectPreset.class);
        ...
        return json(stored);
    } catch (Exception e) {
        return error(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
    }
}
```

Conventions copied from copilot:
- Every mutating route checks `PermissionType.WRITE`, read routes `PermissionType.READ`
  (or `OPEN_ROUTE` for health).
- Errors return HTTP status + `{success:false, error:"..."}` style JSON.
- One Gson instance from a shared factory.

### 2.4 Deployment workflow

```powershell
$env:IGNITION_DEV_GATEWAY = "http://localhost:8088"   # default anyway
.\gradlew.bat clean build     # artifacts in <module>/build/libs/
.\gradlew.bat deployModl      # installs unsigned .modl into the running gateway
```

Verified live on this machine: gateway **is running** at `http://localhost:8088`
(web ping returns 200), and `ai-perspective-builder.unsigned.modl` is already installed in
`user-lib\modules\`. Unsigned modules require a dev/maker edition gateway — satisfied here.

After every deploy we check `C:\Program Files\Inductive Automation\Ignition\logs\wrapper.log`
(per workspace AGENTS.md rule).

---

## 3. New module design

### 3.1 Layout

Single subproject — no Designer scope, no shared DTOs needed:

```
ignition-agent-tools/
├── DESIGN.md                  ← this document
├── README.md                  ← build/deploy/usage quickstart (created in phase 5)
├── settings.gradle            # rootProject.name = ignition-agent-tools; IA nexus repos
├── gradle.properties          # group=com.axcend.ignition, version=0.1.0-SNAPSHOT,
│                              # ignitionVersion=8.3.0, moduleVendor=Axcend
├── build.gradle               # io.ia.sdk.modl; projectScopes [':gateway':'G'];
│                              # hooks ['...AgentToolsHook':'G']; skipModlSigning=true
├── gradlew.bat, gradle/wrapper/*   ← copied verbatim from ignition-copilot-designer
└── gateway/
    ├── build.gradle           # compileOnly gateway-api + ignition-common; JUnit 5
    └── src/
        ├── main/java/com/axcend/ignition/agenttools/
        │   ├── AgentToolsHook.java              # AbstractGatewayModuleHook
        │   ├── AgentToolsRouteHandlers.java     # all route handler methods
        │   ├── validate/
        │   │   ├── PerspectiveViewValidator.java# pure logic, unit-testable
        │   │   ├── ValidationIssue.java         # {path, code, severity, message}
        │   │   └── ComponentCatalog.java        # bundled known ia.* ids (JSON resource)
        │   ├── script/GatewayScriptService.java # Jython execution engine
        │   ├── tags/TagToolsService.java        # browse/read via TagManager
        │   ├── queries/NamedQueryService.java   # execute via NamedQueryManager
        │   └── resources/ProjectResourceService.java # via ProjectManager
        └── test/java/com/axcend/ignition/agenttools/
            ├── PerspectiveViewValidatorTest.java
            └── ... (pure-logic tests, no gateway required)
```

### 3.2 Endpoint specification

All routes mounted under `/agent-tools/`. All requests/responses are JSON unless noted.

#### `GET /agent-tools/health` — OPEN_ROUTE

```json
{ "status": "ok", "module": "com.axcend.ignition.agenttools", "version": "0.1.0",
  "capabilities": ["view.validate","script.exec","gateway.info","tags.browse",
                   "tags.read","query.run","projects.resources"] }
```

#### `POST /agent-tools/view/validate` — READ

Request (inline JSON preferred; `filePath` convenience for files already on disk):

```json
{ "viewJson": { "meta": {...}, "props": {...}, "type": "ia.container.flex", "children": [...] } }
```

Response:

```json
{ "valid": false,
  "errors":   [ { "path": "$.children[2]", "code": "UNKNOWN_COMPONENT_TYPE",
                  "message": "ia.chart.pie is not in the known component catalog" } ],
  "warnings": [ { "path": "$.children[0].layout", "code": "FLEX_SUSPICIOUS_GROW",
                  "message": "grow=0 with flexPosition none may collapse" } ],
  "stats": { "componentCount": 12, "maxDepth": 4, "bindingCount": 7 } }
```

Structural rules (v1):
1. Body parses as JSON object.
2. Root has `meta` (object), `props` (object), `type` (non-empty string).
3. Every node: `type` non-empty string; `meta.name` present; `children` array if present.
4. Recursive descent with JSON-path-style `$` locations for every issue.
5. `type` values starting `ia.` checked against bundled catalog → unknown = **warning**
   (catalog can lag new components); non-`ia.` ids allowed (third-party modules) = info.
6. Flex layout sanity: numeric `grow`/`shrink`, string `basis`; flag obvious collapses.
7. Binding objects: `type` + `config` present when a property value looks like a binding
   (`{"type": ..., "config": ...}` shape detection).
8. `propConfig` entries map to objects; `events` entries have recognized shapes.

Never throws on malformed input — always returns `valid:false` with errors.

#### `POST /agent-tools/script/exec` — WRITE (dev-open)

Request:

```json
{ "code": "tagPaths = system.tag.browse('[default]Line1').toList()\nlen(tagPaths)",
  "timeoutMs": 10000 }
```

Response:

```json
{ "success": true, "result": "42", "repr": "'42'",
  "stdout": "...", "stderr": "", "traceback": null, "durationMs": 183 }
```

Implementation notes:
- Execute via the gateway's `ScriptManager` so scripts see real gateway-scope globals
  (`system.*`, tag providers, etc.). Exact API surface to be confirmed in phase 3 by
  inspecting the local jars (`lib\core\common\common.jar`) with `javap` before coding.
- Fresh locals namespace per call (stateless; no cross-call leakage).
- stdout/stderr captured per call and returned.
- Runs on a dedicated executor thread; `timeoutMs` (clamped 1s–60s, default 10s) returns a
  timeout error. Jython threads cannot be force-killed — documented limitation; runaway
  loops block one pooled thread until they finish.
- Result serialization: try JSON-friendly conversion, fall back to Python `repr()`.

#### `GET /agent-tools/gateway/info` — READ

```json
{ "gatewayName": "...", "version": "8.3.x", "edition": "...",
  "modules": [ { "id": "...", "name": "...", "version": "...", "state": "RUNNING" } ],
  "tagProviders": ["default", "..."],
  "projects": ["Chart_dashboard", "TSPL", "..."] }
```

#### `POST /agent-tools/tags/browse` — READ

Request `{ "provider": "default", "path": "Line1" }` → array of children:
`[{name, path, tagType ("Folder"/"AtomicTag"/"UdtInstance"), dataType, ...}]`.

#### `POST /agent-tools/tags/read` — READ

Request `{ "provider": "default", "paths": ["[default]Line1/Motor/Speed"] }` →
`[{path, value, type, quality, timestamp}]` (bad paths reported per-entry, not fatal).

#### `POST /agent-tools/query/run` — WRITE

Request `{ "project": "TSPL", "queryPath": "MyQueries/ActiveAlarms", "parameters": {"line": "L1"} }`
→ `{ "columns": [...], "rows": [[...]], "rowCount": n }` (queries returning scalars wrap them).

#### `GET /agent-tools/projects/{name}/resources?type=perspective/view` — READ

→ `[{ "resourceType": "perspective/view", "path": "folder/view-name", "files": [...] }]`.
Gives the agent server-side ground truth instead of guessing from folder listings.

### 3.3 Security model (decided)

- Script exec and query runner: **dev-open** — enabled by default, protected by standard
  gateway authentication via `PermissionType.WRITE` (same posture as copilot's mutating
  routes). Acceptable because this is a single-user development machine.
- All other routes: `PermissionType.READ` or open (health only).
- No tag writes / resource writes in v1 by design.
- If this module ever leaves the dev machine: add a config-gated enable flag + shared-secret
  header before shipping (documented in §7 as future work).

---

## 4. Implementation phases

Each phase ends with a green build and (from phase 1 on) a deployed, smoke-tested module.

| Phase | Deliverable | Exit criteria |
|-------|-------------|---------------|
| **1. Scaffold** | settings/build/properties/wrapper copied from copilot; `AgentToolsHook` + `/health` route | `gradlew build` succeeds; `deployModl` installs; `Invoke-RestMethod http://localhost:8088/data/agent-tools/health` returns ok; wrapper.log clean of module errors |
| **2. View validator** | `PerspectiveViewValidator` + JUnit tests + route | Unit tests pass incl. malformed-JSON cases; endpoint validates a real view from `Template_Library` correctly |
| **3. Script exec** | API confirmed via `javap` on local jars; `GatewayScriptService` + route | `system.util.getProperty("user.dir")` round-trips; timeout case returns error; traceback surfaces for bad code |
| **4. Info + tags + queries + resources** | Four services + routes | Live checks against `[default]` provider and an existing project's named query |
| **5. Docs & agent skill** | `README.md`; `.agents/skills/agent-tools/SKILL.md` with PowerShell examples per endpoint; short pointer section added to workspace `AGENTS.md` | A fresh agent session can discover and call every endpoint from the skill doc alone |
| **6. Full verification** | Rebuild, redeploy, exercise all endpoints end-to-end | Checklist in README passes; logs reviewed |

Phase 1 deliberately de-risks the toolchain first — everything after it is incremental Java.

---

## 5. Testing strategy

- **Unit (offline)**: validator is pure logic over parsed JSON — table-driven JUnit tests for
  each rule + fuzz-ish malformed inputs. DTO serialization tests like copilot's.
- **Integration (live gateway)**: PowerShell snippets kept in README; run after each deploy.
  - health → 200
  - validate a known-good template view → `valid:true`
  - validate hand-broken view → expected error codes
  - script exec happy path / syntax error / timeout
  - tags read on a real tag; query run on a real named query; resources list on a real project
- **Log hygiene**: wrapper.log checked after every deploy (workspace rule).

---

## 6. Agent integration

After phase 5, `.agents/skills/agent-tools/SKILL.md` becomes part of the workspace skill
system (loaded via the normal skill mechanism). It documents:

- Base URL and auth expectations (gateway session / same-origin dev usage).
- One ready-to-paste `Invoke-RestMethod` example per endpoint.
- When to use what: validate-before-write for view JSON; script-exec as REPL for testing
  expressions/tag logic; tags/query/resources endpoints as existence checks before referencing.

AGENTS.md gets a two-line pointer so routing picks the skill up automatically.

---

## 7. Risks & future work

| Risk | Mitigation |
|------|-----------|
| `ScriptManager` internal API differs from expectation | Phase 3 starts with `javap` inspection of local jars; fallback is direct `PySystemState`/`PythonInterpreter` setup with gateway globals copied |
| NamedQueryManager / TagManager signatures vary across 8.3.x minors | Same javap-first approach; pin `ignitionVersion=8.3.0` to match installed gateway |
| Script exec blocks a thread forever on infinite loop | Timeout returns error to caller; executor sized so one stuck thread doesn't starve other tools; documented limitation |
| Component catalog drifts from installed Perspective version | Unknown `ia.*` ids are warnings, not errors; catalog regenerated later from live registry (deep validation, deferred) |
| Security if reused outside dev | Future: config-gated enable flag, shared-secret header, IP allow-list |

Future candidates (post-v1): deep validation against live Perspective component registry,
tag write with dry-run preview, UDT definition inspection, alarm status queries,
script-exec sessions with persistent namespace, OpenAPI registration of the routes.
