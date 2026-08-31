# Changelog — Ignition Agent Skills

## August 2026

### Native Component Schema Validation (2026-08-30)

**Created:**
- `PerspectiveComponentSchemaCatalog.java` — Loads the native Perspective component descriptor files
  (`ia.components.json`, `barcode.component.json`, `perspective-timeseries.components.json`,
  `perspective-googlemap.components.json`, `perspective-amcharts.components.json`,
  `perspective-map.components.json`, `pdf-viewer.components.json`) from the `perspective-common`
  classpath and compiles each component's `props` JSON Schema into a native `JsonSchema`.
  URN `$ref` props (`style`, `textStyle`, ...) are pre-resolved into standalone native schemas
  (the native `RefValidator` silently skips `urn:ignition-schema:` refs on factory-built schemas).

**Updated:**
- `PerspectiveViewValidator.java` — Each component's `props` object is now validated against its
  native schema when the component is in the catalog (wrong types, bad enums, unknown props where
  `additionalProperties` is false, missing required props → `SCHEMA_*` `ValidationIssue`s at
  ERROR severity). Binding-valued props are exempted from type checks to avoid false positives;
  unknown/unavailable components fall back to structural checks only.
- `JsonSchemaValidator.java` — Corrected javadoc (no longer claims no reusable Perspective schemas
  exist on the classpath).
- `AgentToolsRouteHandlers.java` — Added missing `ValidationIssue` import (pre-existing compile error).
- `api-tracking/` — `native-apis-used.md`, `custom-implementations.md`, `investigated-not-adopted.md`
  updated for the `JsonSchema`/component-schema adoption, `ComponentRegistry`/`RefValidator`
  findings, and the `ResourceValidator` investigation (no gateway-side path).

**Tests:**
- `PerspectiveComponentSchemaCatalogTest.java` — New: catalog loads all 7 descriptor files, native
  schema compilation, required/enum violations, URN-ref standalone style validation.
- `PerspectiveViewValidatorTest.java` — New `SchemaValidation` suite (required/enum/additionalProps,
  URN-ref style, binding exemption, literal type, alias→canonical schema); existing fixtures updated
  to schema-conformant `props`.

**Documentation:**
- `doc/NATIVE-COMPONENT-SCHEMA-VALIDATION.md` — New plain-language summary: what was done, the native
  Ignition functions used, and what the validation now enables.

### Diagnostic System Implementation

**Created:**
- `diagnostic/` — New diagnostic package for view analysis
  - `DiagnosticService.java` — Core diagnostic engine (read view JSON, walk tree, validate)
  - `LogCaptureService.java` — Gateway log capture and filtering
  - `DiagnosticCollector.java` — Common interface for collecting issues
  - `ViewDiagnostics.java` — View-level diagnostic result
  - `ComponentDiagnostics.java` — Component-level diagnostic result
  - `BindingDiagnostics.java` — Binding-level diagnostic result
  - `DiagnosticIssue.java` — Issue record with error codes and paths
  - `ViewStats.java` — View statistics record

**Updated:**
- `AgentToolsHook.java` — Added 4 diagnostic routes (`/diagnostics/view`, `/diagnostics/component`, `/diagnostics/binding`, `/diagnostics/logs`)
- `AgentToolsRouteHandlers.java` — Added diagnostic handler methods
- `GatewayIntrospectionService.java` — Added `findNamedQuery()` method for diagnostic checks
- `README.md` — Added diagnostic endpoints to table, updated layout
- `SKILL-ARCHITECTURE.md` — Added diagnostic system section

**Created:**
- `doc/DIAGNOSTIC-SYSTEM.md` — Comprehensive documentation for diagnostic system

### Initial Build

**Created:**
- `ignition-conventions/` — New top-level skill folder for patterns and conventions
  - `SKILL.md` — Router with LOAD FIRST emphasis
  - `ignition-project-structure/` — Project structure reference (~943 lines)
  - `common-ignition-patterns/` — 8 pattern skills:
    - `bindings-and-transforms/` — Binding decision tree, transform patterns
    - `scripting-conventions/` — Jython 2.7 patterns, system.* API
    - `common-components/` — Top 10 components with typical props
    - `view-layout-and-position/` — Flex sizing, scrollbar prevention
    - `view-structure-and-properties/` — view.json anatomy, propConfig
    - `page-config-and-navigation/` — Routes, nav menus, docks
    - `styles-and-css/` — CSS variables, classes, responsive
    - `common-data-patterns/` — Dataset/JSON transforms, sqlType values

**Moved:**
- `ignition-project-structure/` from `ignition-perspective-skills/` → `ignition-conventions/`
- `common-ignition-patterns/` from `ignition-perspective-skills/` → `ignition-conventions/`
- `ignition-tags/` from `ignition-perspective-skills/` → `ignition-openapi/`
- `config.example.json` from `ignition-tags/` → `ignition-openapi/` root

**Updated:**
- `ignition-perspective-skills/SKILL.md` — Removed common patterns section, removed ignition-tags references
- `ignition-openapi/SKILL.md` — Added ignition-tags sub-skill section
- `ignition-tags/SKILL.md` — Updated script paths to new location
- `AGENTS.md` — Added ignition-conventions to mandatory process, updated architecture tree
- `README.md` — Updated file locations reference, structure verification

**Created:**
- `ignition-agent-tools/` — Documentation folder
  - `SKILL-ARCHITECTURE.md` — Complete architecture reference
  - `QUICK-START.md` — Quick reference for agents and developers
  - `CHANGELOG.md` — This file
