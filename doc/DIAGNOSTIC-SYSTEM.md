# Diagnostic System Implementation

## Overview

The diagnostic system provides AI agents with the ability to analyze Perspective views, components, and bindings for errors, warnings, and best practices. This enables a write→diagnose→fix feedback loop for automated view development.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    AI Agent                              │
│  (writes view → calls diagnostic → reads issues → fixes)│
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│              DiagnosticService                          │
│  - Reads view JSON from project resources               │
│  - Parses Perspective document structure                │
│  - Walks component tree                                 │
│  - Validates component types against catalog            │
│  - Validates bindings (tag, query, expression, property)│
│  - Checks named query existence                         │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│              LogCaptureService                           │
│  - Reads wrapper.log from Ignition log directory        │
│  - Parses log entries (timestamp, level, thread, msg)   │
│  - Filters by project, pattern, errors-only             │
│  - Returns recent entries for debugging                 │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│              DiagnosticCollector (Interface)             │
│  - Common interface for all diagnostic builders         │
│  - Ensures consistent error/warning collection          │
└─────────────────────────────────────────────────────────┘
```

## Components

### DiagnosticService.java
Core diagnostic engine that reads view JSON from project resources and performs static analysis.

**Methods:**
- `getViewDiagnostics(projectName, viewPath)` → ViewDiagnostics
- `getComponentDiagnostics(projectName, viewPath, componentPath)` → ComponentDiagnostics
- `getBindingDiagnostics(projectName, viewPath, componentPath, propertyPath)` → BindingDiagnostics

**Features:**
- Reads view JSON directly from project resources (no designer needed)
- Validates component types against ComponentCatalog
- Validates bindings (tag, query, expression, property)
- Checks named query existence in project
- Walks component tree with depth limit
- Handles deprecated type aliases
- Reports duplicate component names

### LogCaptureService.java
Captures and filters gateway log entries.

**Methods:**
- `getRecentEntries(count, projectName, pattern, errorsOnly)` → List<LogEntry>
- `getEntriesAroundError(projectName, contextLines, beforeLines)` → List<LogEntry>
- `containsLogEntry(projectName, pattern, lines)` → boolean

**Features:**
- Parses wrapper.log format
- Filters by project name
- Supports regex patterns
- Errors-only mode
- Context around error lines

### DiagnosticCollector.java
Common interface for collecting diagnostic issues.

```java
public interface DiagnosticCollector {
    DiagnosticCollector addError(DiagnosticIssue issue);
    DiagnosticCollector addWarning(DiagnosticIssue issue);
}
```

### ViewDiagnostics.java
View-level diagnostic result with component and binding counts.

**Fields:**
- `viewPath` — Path to the view
- `valid` — Whether no errors were found
- `errors` — List of DiagnosticIssue (severity ERROR)
- `warnings` — List of DiagnosticIssue (severity WARNING)
- `componentCount` — Total components in view
- `bindingCount` — Total bindings found

### ComponentDiagnostics.java
Component-level diagnostic result.

**Fields:**
- `viewPath`, `componentPath` — Paths
- `componentType` — Component type (e.g., `ia.display.label`)
- `componentName` — Component name from meta.name
- `valid` — Whether no errors were found
- `errors`, `warnings` — Lists of DiagnosticIssue

### BindingDiagnostics.java
Binding-level diagnostic result.

**Fields:**
- `viewPath`, `componentPath`, `propertyPath` — Paths
- `bindingType` — Binding type (tag, query, expression, property)
- `valid` — Whether no errors were found
- `quality` — Quality string (Good, No Binding, etc.)
- `message` — Optional diagnostic message
- `errors` — List of DiagnosticIssue

### DiagnosticIssue.java
Issue record with error codes, categories, and paths.

**Fields:**
- `code` — Error code (e.g., `MISSING_COMPONENT_TYPE`)
- `severity` — ERROR or WARNING
- `category` — STRUCTURE, COMPONENT, BINDING, QUERY, STYLE
- `message` — Human-readable description
- `path` — JSON path to the issue

**Error Codes:**
- `VIEW_NOT_FOUND` — View not found in project
- `INVALID_VIEW_DOCUMENT` — View JSON is not a valid Perspective document
- `MISSING_ROOT_COMPONENT` — View has no root component
- `MISSING_COMPONENT_TYPE` — Component has no `type` field
- `DEPRECATED_TYPE_ALIAS` — Component type is a deprecated alias
- `UNKNOWN_COMPONENT_TYPE` — Component type not in standard catalog
- `EMPTY_TAG_PATH` — Tag binding has empty `tagPath`
- `MISSING_BINDING_TYPE` — Binding has no `type` field
- `MISSING_BINDING_CONFIG` — Binding has no `config` object
- `INVALID_TAG_PATH` — Tag path format is invalid
- `TAG_PROVIDER_NOT_FOUND` — Tag provider not found for tag path source
- `TAG_NOT_FOUND` — Tag not found at specified path
- `TAG_QUALITY_NOT_GOOD` — Tag exists but quality is not Good
- `TAG_VALIDATION_ERROR` — Error occurred during tag validation
- `QUERY_NOT_FOUND` — Named query not found in project
- `INVALID_QUERY_PARAMETERS` — Query binding parameters must be an object
- `INVALID_POLL_RATE` — Query binding pollRate must be a valid number
- `MISSING_EXPRESSION` — Expression binding has no expression
- `EXPRESSION_SYNTAX_ERROR` — Expression has syntax error (unmatched parentheses)
- `PYTHON_IMPORT_IN_EXPRESSION` — Python imports not valid in expressions
- `CLIENT_SCOPE_FUNCTION_IN_EXPRESSION` — Client-scope functions used in gateway scope
- `INVALID_RUNSCRIPT_PATH` — runScript() module path should be dot-separated
- `NEGATIVE_RUNSCRIPT_POLL_RATE` — runScript() pollRate should not be negative
- `INVALID_RUNSCRIPT_POLL_RATE` — runScript() pollRate must be a number
- `MISSING_PROPERTY_PATH` — Property binding has no property path
- `INVALID_PROPERTY_PATH` — Invalid property path format
- `UNKNOWN_BINDING_TYPE` — Binding type not recognized
- `COMPONENT_NOT_FOUND` — Component not found at specified path
- `DIAGNOSTIC_ERROR` — Internal error during diagnostic analysis

### ViewStats.java
View statistics record.

**Fields:**
- `componentCount` — Total components
- `bindingCount` — Total bindings
- `maxDepth` — Maximum nesting depth

## API Endpoints

### GET /diagnostics/view
Get comprehensive diagnostics for a Perspective view.

**Parameters:**
- `project` (required) — Project name
- `view` (required) — View path (e.g., `Dashboard/Main`)

**Response:**
```json
{
  "viewPath": "Dashboard/Main",
  "valid": false,
  "errors": [
    {
      "code": "QUERY_NOT_FOUND",
      "severity": "ERROR",
      "category": "QUERY",
      "message": "Named query 'GetAlarms' not found in project 'MyProject'",
      "path": "root/Table.props.data"
    }
  ],
  "warnings": [
    {
      "code": "UNKNOWN_COMPONENT_TYPE",
      "severity": "WARNING",
      "category": "COMPONENT",
      "message": "Component type 'custom.MyComponent' is not in the standard catalog",
      "path": "root/CustomContainer/MyComponent"
    }
  ],
  "componentCount": 15,
  "bindingCount": 8
}
```

### GET /diagnostics/component
Get diagnostics for a specific component.

**Parameters:**
- `project` (required) — Project name
- `view` (required) — View path
- `path` (required) — Component path (e.g., `root/Table`)

**Response:**
```json
{
  "viewPath": "Dashboard/Main",
  "componentPath": "root/Table",
  "componentType": "ia.display.table",
  "componentName": "DataTable",
  "valid": true,
  "errors": [],
  "warnings": []
}
```

### GET /diagnostics/binding
Get diagnostics for a specific binding.

**Parameters:**
- `project` (required) — Project name
- `view` (required) — View path
- `path` (required) — Component path
- `property` (required) — Property path (e.g., `props.data`)

**Response:**
```json
{
  "viewPath": "Dashboard/Main",
  "componentPath": "root/Table",
  "propertyPath": "props.data",
  "bindingType": "query",
  "valid": true,
  "quality": "Good",
  "message": null,
  "errors": []
}
```

### GET /diagnostics/logs
Get recent gateway log entries.

**Parameters:**
- `count` (optional, default 100) — Number of entries
- `project` (optional) — Filter by project name
- `pattern` (optional) — Regex pattern to match
- `errorsOnly` (optional, default false) — Only ERROR/WARN entries

**Response:**
```json
{
  "count": 2,
  "entries": [
    {
      "level": "ERROR",
      "timestamp": "2026/08/25 14:30:15",
      "message": "Named query 'GetAlarms' not found in project 'MyProject'"
    }
  ]
}
```

## Usage Example

### AI Agent Workflow

1. **Write view** — Agent creates/modifies a Perspective view
2. **Call diagnostic** — Agent calls `GET /diagnostics/view?project=MyProject&view=Dashboard/Main`
3. **Read issues** — Agent receives list of errors and warnings
4. **Fix issues** — Agent modifies view to address each error
5. **Re-diagnose** — Agent calls diagnostic again to verify fixes

### Example Diagnostic Call

```bash
# Diagnose a view
curl "http://localhost:8088/data/agent-tools/diagnostics/view?project=MyProject&view=Dashboard/Main"

# Diagnose a specific component
curl "http://localhost:8088/data/agent-tools/diagnostics/component?project=MyProject&view=Dashboard/Main&path=root/Table"

# Diagnose a binding
curl "http://localhost:8088/data/agent-tools/diagnostics/binding?project=MyProject&view=Dashboard/Main&path=root/Table&property=props.data"

# Get recent errors
curl "http://localhost:8088/data/agent-tools/diagnostics/logs?project=MyProject&errorsOnly=true&count=50"
```

## Integration with Existing System

The diagnostic system integrates with:

- **ComponentCatalog** — For validating component types
- **GatewayIntrospectionService** — For checking named query existence
- **PerspectiveViewValidator** — For structural + native-schema validation (complementary; since
  2026-08-30 it validates component `props` against the native Perspective component schemas via
  `PerspectiveComponentSchemaCatalog` — `SCHEMA_*` codes)
- **LogCaptureService** — For runtime debugging

## Design Decisions

1. **Static Analysis First** — Read view JSON from project resources rather than requiring a designer session
2. **Project Resource Access** — Uses ProjectManager to read view files directly from disk
3. **Component Catalog** — Validates against known component types from the SDK
4. **Named Query Checks** — Verifies named queries exist before reporting binding errors
5. **Error Codes** — Machine-readable codes for programmatic handling
6. **Paths** — JSON paths to issues for easy navigation

## Future Enhancements

1. **Post-Write Verification** — Combine write + diagnostics in single workflow
2. **Binding Value Inspection** — Read actual binding values at runtime
3. **Style Validation** — Check CSS styles against best practices
4. **Performance Metrics** — Track view load times and component counts
5. **Dependency Analysis** — Find all views that reference a component/query
