# AGENTS.md — Agent Guidelines

Guidelines for any AI agent working on the `ignition-agent-tools` module.

## Core Principle: Prefer Ignition-Native Infrastructure

This module exists to let AI agents reliably generate, validate, and fix Ignition/Perspective
projects. **Ignore** the temptation to re-implement what Ignition already provides. Before writing
any custom validator, parser, or data-access code, **check whether Ignition exposes a native SDK
API that already does it.**

Ignition already ships substantial validation/parsing infrastructure. Recreating it with regex or
hand-rolled heuristics produces worse, less actionable, and unloadable diagnostics. Prefer the
native API, and only fall back to custom code when **verified** that no suitable native API exists.

---

## MANDATORY: Check the MCP Before Building Custom Code

An Ignition Javadoc MCP server lives in [`Ignition-JavaDoc-MCP/`](Ignition-JavaDoc-MCP/). Whenever
you plan to implement something that parses, validates, reads, writes, or executes anything
Ignition-related, **query the MCP first** to confirm whether the capability already exists in the SDK.

### Available MCP tools
- `search_ignition_api` — search classes/packages/members by name (e.g. `query: "TagPathValidator"`).
  **Always pass `version: "8.3.7"`** — the default is `8.1.39` and APIs differ between versions.
- `get_ignition_class_docs` — full docs for a class (`packagePath`, `className`, `version`).
- `get_ignition_package_docs` — list classes in a package (`packagePath`, `version`).
- `get_ignition_member_docs` — docs for a specific method/field (`memberAnchor` from search results).

### Required workflow
```
Task requires Ignition parsing/validation/read/write/execute
        │
        ▼
Search the MCP (search_ignition_api, version=8.3.7)
        │
        ├── Native API exists ──► Use it. Update api-tracking/ (see below).
        │
        └── No obvious native API
                │
                ▼
        Verify carefully (get_member_docs / class docs /
        inspect actual runtime jars, not just Javadoc)
                │
        ├── Native API confirmed ──► Use it. Update api-tracking/.
        │
        └── Genuinely no native API ──► Implement custom; document in api-tracking/
                                         why no native API exists.
```

### Hard rules
- **Always verify against 8.3.7** (the module's target). The MCP default version (`8.1.39`) is WRONG
  for this project — never rely on it.
- **Distinguish Gateway-safe from Designer-only.** This is a **Gateway** module. Designer/client
  APIs (e.g. `com.inductiveautomation.ignition.designer.gui.validation.*`) usually CANNOT be used.
  Confirm the class is in a gateway-safe package before adopting it.
- **Javadoc existence is not enough** — also check that the runtime class is actually available
  (it may live only in a POM-only dependency, or only in the installed gateway, not on the module's
  compile classpath). Inspect the JARs if unsure.
- **Do NOT reinvent** what Ignition already provides. Known native building blocks you should prefer:
  - `com.inductiveautomation.ignition.common.tags.paths.TagPathValidator` — tag-path validation
  - `com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser` — tag-path parsing
  - `com.inductiveautomation.perspective.gateway.binding.*` / `.binding.expression.*` — expressions
  - `com.inductiveautomation.ignition.gateway.config.ResourceValidator` / `ValidationErrors` — resource validation
  - `com.inductiveautomation.ignition.common.jsonschema.JsonSchema` / `JsonSchemaFactory` — JSON validation
  - **Perspective component `props` schemas** — `perspective-common` ships `ia.components.json` and
    friends (the same `*.components.json` `ComponentRegistry` reads). `PerspectiveComponentSchemaCatalog`
    compiles them into native `JsonSchema`s. Caveat: native `RefValidator` silently skips
    `urn:ignition-schema:` refs on factory-built schemas — URN-ref props (`style`, `textStyle`, …)
    must be pre-resolved into standalone schemas to be checkable.

---

## Track API Usage (REQUIRED)

Every feature in this module must be recorded in the **`api-tracking/`** directory so future builds
are grounded in what is actually used, not what an agent assumed.

### What to record
1. **Ignition-native APIs actually used** — the fully-qualified class + method, what it's used for,
   and where (file:line).
2. **Features implemented with custom code** — what we built ourselves, which API (if any) backs it,
   and **why** no native API was used.
3. **Candidate native APIs investigated but NOT adopted** — the API, and the reason (e.g. Designer-only,
   not on classpath, wrong version).

### When to update
- **Before** writing any new custom code that could have a native equivalent: note in the relevant
  file that the MCP was checked and what it returned.
- **After** every change that adds or changes an Ignition API call or a custom validation rule.

---

## Quality Bar
- Always verify solutions compile against the real Ignition 8.3.7 classes (`.\gradlew.bat :gateway:compileJava`).
- Run tests before finishing (`.\gradlew.bat :gateway:test`).
- Prefer actionable diagnostics with `suggestions` the agent can act on, grounded in real Ignition
  error messages rather than custom heuristics.
- Never commit secrets; keep commits focused and only when asked.
