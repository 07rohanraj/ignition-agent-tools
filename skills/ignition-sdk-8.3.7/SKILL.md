---
name: ignition-sdk-8.3.7
description: Ignition 8.3.7 SDK knowledge layer. Provides API index, task→API mappings, and versioned SDK reference for agents. Load BEFORE implementing any Ignition API code.
---

# Ignition 8.3.7 SDK Knowledge Layer

## Purpose

This skill provides a versioned SDK knowledge layer for Ignition 8.3.7. It is NOT a replacement for the official Javadocs — it is a **map** that helps agents find the right APIs quickly without hallucinating.

**Source of truth:** Official Ignition 8.3.7 SDK Javadocs
**This skill:** Verified API index + task mappings + patterns

---

## Critical Rules

### API Verification Procedure (MANDATORY)

Before using ANY Ignition API:

```
Agent wants to use Ignition API
          │
          ▼
Find candidate class in this skill
          │
          ▼
Does class exist in 8.3.7?
     │            │
    NO           YES
     │            │
     ▼            ▼
Find alternative  Verify method signature
                   │
                   ▼
              Verify parameters
                   │
                   ▼
              Verify return type
                   │
                   ▼
              Verify visibility
                   │
                   ▼
                 Code
```

### Forbidden Assumptions

| Assumption | Status | Why |
|------------|--------|-----|
| 8.1 API == 8.3 API | **FORBIDDEN** | APIs change between versions |
| Internal class == supported SDK API | **FORBIDDEN** | Internal classes may be removed |
| Forum example works in 8.3.7 | **FORBIDDEN** | Examples may be outdated |
| Method exists because similar name exists | **FORBIDDEN** | Verify exact signature |
| Class name from memory is evidence | **FORBIDDEN** | Always verify in SDK |

### Before Using an API

Verify ALL of the following:
- [ ] Class exists in 8.3.7 SDK
- [ ] Package is correct
- [ ] Method name is exact
- [ ] Parameters match
- [ ] Return type is correct
- [ ] Visibility (public/protected) is accessible
- [ ] Version compatibility confirmed

---

## API Index by Domain

### Perspective Designer

**Use when:**
- Interacting with Perspective Designer
- Inspecting Views
- Accessing Designer state
- Creating Designer integrations

**Relevant APIs:**
- `com.inductiveautomation.perspective.designer.PerspectiveDesignerInterface`
- `com.inductiveautomation.perspective.designer.PerspectiveDesignSession`
- `com.inductiveautomation.perspective.DesignerBindingRegistry`
- `com.inductiveautomation.perspective.BindingDesignDelegate`
- `com.inductiveautomation.perspective.PerspectiveElement`

**Authoritative reference:**
https://sdk.inductiveautomation.com/javadoc/ignition83/8.3.7/

---

### Expression Bindings

**Use when:**
- Reading expression bindings
- Creating expression bindings
- Parsing expressions
- Evaluating expressions
- Implementing binding validation

**Relevant APIs:**
- `com.inductiveautomation.perspective.gateway.binding.expression.ExpressionBinding`
- `com.inductiveautomation.perspective.gateway.binding.expression.ExpressionBindingFactory`
- `com.inductiveautomation.perspective.gateway.binding.ExpressionBindingParseContext`
- `com.inductiveautomation.perspective.gateway.binding.PerspectiveExpression`
- `com.inductiveautomation.perspective.gateway.binding.PerspectiveExpressionFactory`

**Before implementation:**
Verify exact signatures in the 8.3.7 SDK.

---

### Tag Bindings

**Use when:**
- Reading/writing tag bindings
- Subscribing to tag changes
- Working with tag paths

**Relevant APIs:**
- `com.inductiveautomation.perspective.gateway.binding.tag.TagBinding`
- `com.inductiveautomation.perspective.gateway.binding.tag.TagBindingConfig`
- `com.inductiveautomation.ignition.common.tags.paths.TagPath`
- `com.inductiveautomation.ignition.common.tags.config.TagConfiguration`

---

### Query Bindings

**Use when:**
- Executing named queries
- Binding to query results
- Working with datasets

**Relevant APIs:**
- `com.inductiveautomation.perspective.gateway.binding.query.QueryBinding`
- `com.inductiveautomation.perspective.gateway.binding.query.QueryBindingConfig`
- `com.inductiveautomation.ignition.common.datasets.Dataset`

---

### Gateway Lifecycle

**Use when:**
- Creating gateway hooks
- Managing module lifecycle
- Accessing GatewayContext

**Relevant APIs:**
- `com.inductiveautomation.ignition.common.gateway.AbstractGatewayModuleHook`
- `com.inductiveautomation.ignition.common.gateway.GatewayContext`
- `com.inductiveautomation.ignition.common.gateway.LicenseState`
- `com.inductiveautomation.ignition.common.gateway.RouteGroup`

---

### Script Execution

**Use when:**
- Running Jython scripts
- Accessing system.* functions
- Working with ScriptManager

**Relevant APIs:**
- `com.inductiveautomation.ignition.common.script.hints.PropertiesFile`
- `com.inductiveautomation.ignition.common.scripting.ScriptManager`
- `com.inductiveautomation.perspective.gateway.script.PerspectiveScriptModule`

---

### Project Resources

**Use when:**
- Reading project resources
- Working with view.json
- Accessing named queries

**Relevant APIs:**
- `com.inductiveautomation.ignition.common.project.Project`
- `com.inductiveautomation.ignition.common.project.Resource`
- `com.inductiveautomation.ignition.common.project.ResourceType`

---

### Diagnostics

**Use when:**
- Validating views
- Diagnosing binding errors
- Capturing logs

**Relevant APIs:**
- `com.inductiveautomation.perspective.gateway.binding.BindingContext`
- `com.inductiveautomation.perspective.gateway.binding.BindingDiagnostic`
- `com.inductiveautomation.ignition.common.Diagnostics`

---

## Task → API Mappings

### Task: Validate a Perspective View

**Investigate:**
1. `PerspectiveViewValidator` (our implementation)
2. `ComponentCatalog` (our implementation)
3. `ValidationIssue` (our implementation)

**External APIs (if needed):**
- `com.inductiveautomation.perspective.PerspectiveElement`

**Do NOT:**
- Implement an expression parser
- Copy an 8.1 implementation without verification
- Assume Designer diagnostics are exposed through a method called `validate()`

---

### Task: Execute a Jython Script

**Investigate:**
1. `GatewayScriptService` (our implementation)
2. `ScriptManager` (Ignition SDK)

**External APIs:**
- `com.inductiveautomation.ignition.common.scripting.ScriptManager`

**Do NOT:**
- Assume client-scope functions are available
- Use Python 3 syntax (Jython is 2.7)

---

### Task: Browse Tags

**Investigate:**
1. `GatewayIntrospectionService` (our implementation)
2. Tag browsing APIs (Ignition SDK)

**External APIs:**
- `com.inductiveautomation.ignition.common.tags.paths.TagPath`
- `com.inductiveautomation.ignition.common.tags.model.TagProvider`

---

### Task: Run a Named Query

**Investigate:**
1. `GatewayIntrospectionService` (our implementation)
2. Query execution APIs (Ignition SDK)

**External APIs:**
- `com.inductiveautomation.ignition.common.datasets.Dataset`

---

## Verified APIs (From Our Implementation)

These APIs have been verified to work in Ignition 8.3.7 through our module implementation:

### Gateway Hook
- `com.inductiveautomation.ignition.common.gateway.AbstractGatewayModuleHook`
  - `setup(GatewayContext)` ✓
  - `startup(LicenseState)` ✓
  - `shutdown()` ✓
  - `mountRouteHandlers(RouteGroup)` ✓

### Route Registration
- `com.inductiveautomation.ignition.common.gateway.RouteGroup`
  - `newRoute(String)` ✓
  - `type(String)` ✓
  - `handler(RouteHandler)` ✓
  - `method(HttpMethod)` ✓
  - `accessControl(AccessControlStrategy)` ✓
  - `mount()` ✓

### Script Execution
- `com.inductiveautomation.ignition.common.scripting.ScriptManager`
  - `runScript(String, String, int)` ✓ (through our GatewayScriptService)

---

## Version-Specific Notes

### Ignition 8.3.7 Changes from 8.1.x

| Area | Change | Impact |
|------|--------|--------|
| JDK | JDK 17 required | Must use `--release 17` |
| Scripting | Jython 2.7 (no Python 3) | No f-strings, no type hints |
| Module plugin | `io.ia.sdk.modl` v0.1.1 | Different from older plugins |
| Dependencies | `gateway-api` + `ignition-common` | Both `compileOnly` |

---

## Official Resources

| Resource | URL |
|----------|-----|
| SDK Javadocs (8.3.7) | https://sdk.inductiveautomation.com/javadoc/ignition83/8.3.7/ |
| SDK Examples | https://github.com/inductiveautomation/ignition-sdk-examples (branch: ignition-8.3) |
| Official Docs | https://docs.inductiveautomation.com/ |
| Forum | https://forums.inductiveautomation.com/ |

---

## Pattern Files

See `patterns/` directory for verified implementation patterns:
- `gateway-hook.md` — Gateway hook lifecycle pattern
- `route-registration.md` — REST route registration pattern
- `script-execution.md` — Jython execution pattern
- `view-validation.md` — View validation pattern

---

*This skill is the foundation for all Ignition SDK work. Always verify APIs against the 8.3.7 Javadocs before implementing.*
