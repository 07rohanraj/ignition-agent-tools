---
name: binding-validation
description: Perspective binding validation patterns for Ignition 8.3.7. Covers static validation, dynamic diagnostics, and error detection. Load when validating or diagnosing Perspective bindings.
---

# Perspective Binding Validation (8.3.7)

## Purpose

This skill provides verified patterns for validating Perspective bindings in Ignition 8.3.7. It covers both static validation (structural checks) and dynamic diagnostics (runtime evaluation).

**Source of truth:** Official Ignition 8.3.7 SDK Javadocs + our verified implementation

---

## Architecture

### Static Validation

```
View JSON
   ↓
Binding Config
   ↓
Structural Checks
   ↓
ValidationResult
```

### Dynamic Diagnostics

```
View
   ↓
Perspective Evaluation
   ↓
Ignition Diagnostics
   ↓
DiagnosticIssue
```

---

## Static Validation (Our Implementation)

### PerspectiveViewValidator

Validates view JSON structure without runtime evaluation.

```java
PerspectiveViewValidator validator = new PerspectiveViewValidator();
ComponentCatalog catalog = new ComponentCatalog();

ValidationResult result = validator.validate(viewJson, catalog);
```

### Validation Checks

| Check | Code | Severity |
|-------|------|----------|
| Missing root type | `MISSING_ROOT_TYPE` | ERROR |
| Missing component type | `MISSING_COMPONENT_TYPE` | ERROR |
| Unknown component type | `UNKNOWN_COMPONENT_TYPE` | WARNING |
| Deprecated type alias | `DEPRECATED_ALIAS` | WARNING |
| Missing binding config | `BINDING_MISSING_CONFIG` | ERROR |
| Transforms not array | `TRANSFORMS_NOT_ARRAY` | ERROR |
| propConfig not object | `PROPCONFIG_NOT_OBJECT` | ERROR |
| Events not object | `EVENTS_NOT_OBJECT` | ERROR |
| Children not array | `CHILDREN_NOT_ARRAY` | ERROR |
| Child not object | `CHILD_NOT_OBJECT` | ERROR |
| Duplicate sibling name | `DUPLICATE_SIBLING_NAME` | WARNING |
| Layout not object | `LAYOUT_NOT_OBJECT` | ERROR |
| Layout not numeric | `LAYOUT_NOT_NUMERIC` | WARNING |
| Flex zero grow no basis | `FLEX_ZERO_GROW_NO_BASIS` | WARNING |
| Style layout keys | `STYLE_LAYOUT_KEYS` | WARNING |

---

## Dynamic Diagnostics (Our Implementation)

### DiagnosticService

Evaluates views against the live gateway and captures runtime errors.

```java
DiagnosticService service = new DiagnosticService(context);

ViewDiagnostics result = service.getViewDiagnostics(project, viewPath);
```

### Diagnostic Checks

| Check | Code | Category |
|-------|------|----------|
| View not found | `VIEW_NOT_FOUND` | VIEW |
| Invalid view document | `INVALID_VIEW_DOCUMENT` | VIEW |
| Missing root component | `MISSING_ROOT_COMPONENT` | VIEW |
| Missing component type | `MISSING_COMPONENT_TYPE` | COMPONENT |
| Deprecated type alias | `DEPRECATED_TYPE_ALIAS` | COMPONENT |
| Unknown component type | `UNKNOWN_COMPONENT_TYPE` | COMPONENT |
| Empty tag path | `EMPTY_TAG_PATH` | BINDING |
| Missing binding type | `MISSING_BINDING_TYPE` | BINDING |
| Missing binding config | `MISSING_BINDING_CONFIG` | BINDING |
| Invalid tag path format | `INVALID_TAG_PATH` | BINDING |
| Tag provider not found | `TAG_PROVIDER_NOT_FOUND` | BINDING |
| Tag not found | `TAG_NOT_FOUND` | BINDING |
| Tag quality not good | `TAG_QUALITY_NOT_GOOD` | BINDING |
| Tag validation error | `TAG_VALIDATION_ERROR` | BINDING |
| Query not found | `QUERY_NOT_FOUND` | BINDING |
| Invalid query parameters | `INVALID_QUERY_PARAMETERS` | BINDING |
| Invalid poll rate | `INVALID_POLL_RATE` | BINDING |
| Missing expression | `MISSING_EXPRESSION` | BINDING |
| Expression syntax error | `EXPRESSION_SYNTAX_ERROR` | BINDING |
| Python import in expression | `PYTHON_IMPORT_IN_EXPRESSION` | BINDING |
| Client scope function in expression | `CLIENT_SCOPE_FUNCTION_IN_EXPRESSION` | BINDING |
| Invalid runScript path | `INVALID_RUNSCRIPT_PATH` | BINDING |
| Negative runScript poll rate | `NEGATIVE_RUNSCRIPT_POLL_RATE` | BINDING |
| Invalid runScript poll rate | `INVALID_RUNSCRIPT_POLL_RATE` | BINDING |
| Missing property path | `MISSING_PROPERTY_PATH` | BINDING |
| Invalid property path | `INVALID_PROPERTY_PATH` | BINDING |
| Unknown binding type | `UNKNOWN_BINDING_TYPE` | BINDING |
| Component not found | `COMPONENT_NOT_FOUND` | COMPONENT |
| Diagnostic error | `DIAGNOSTIC_ERROR` | STRUCTURE |

---

## Binding Type Validation

### Expression Binding

**Structure:**
```json
{
  "binding": {
    "config": {
      "expression": "runScript('Module.function', 0)"
    },
    "type": "expr"
  }
}
```

**Validation:**
- [ ] `type` is `"expr"`
- [ ] `config.expression` exists and is non-empty
- [ ] Expression syntax is valid (balanced parentheses)
- [ ] No Python imports (`from X import Y`) - use runScript() instead
- [ ] runScript() calls have valid module path format (dot-separated)
- [ ] runScript() pollRate is a valid non-negative number
- [ ] No client-scope functions (system.perspective, system.gui, system.nav) in gateway scope

**Common Errors:**
- Missing `expression` in config
- Invalid expression syntax (unmatched parentheses)
- Using Python imports
- Invalid runScript() path format
- Negative runScript() pollRate
- Client-scope functions in gateway scope

---

### Tag Binding

**Structure:**
```json
{
  "binding": {
    "config": {
      "tagPath": "[default]Folder/Tag",
      "mode": "read"
    },
    "type": "tag"
  }
}
```

**Inline Shorthand Format:**
```json
{
  "props": {
    "value": {
      "tagPath": "[default]Folder/Tag"
    }
  }
}
```

**Validation:**
- [ ] `type` is `"tag"`
- [ ] `config.tagPath` exists and is non-empty
- [ ] `config.tagPath` is valid format (parsed by TagPathParser)
- [ ] Tag provider exists for the tag path source
- [ ] Tag exists in the provider and has Good quality
- [ ] `config.mode` is valid (`read`, `write`, `readwrite`)

**Common Errors:**
- Empty `tagPath`
- Invalid tag path format
- Tag provider not found
- Tag not found or bad quality
- Missing `mode`

---

### Query Binding

**Structure:**
```json
{
  "binding": {
    "config": {
      "queryPath": "MyFolder/MyQuery",
      "parameters": {},
      "pollRate": 0
    },
    "type": "query"
  }
}
```

**Validation:**
- [ ] `type` is `"query"`
- [ ] `config.queryPath` exists and is non-empty
- [ ] Named query exists in the project
- [ ] `config.parameters` is object (if present)
- [ ] `config.pollRate` is a valid number (if present), non-negative

**Common Errors:**
- Empty `queryPath`
- Query not found in project
- Invalid parameters (not an object)
- Invalid pollRate (not a number or negative)

---

### Property Binding

**Structure:**
```json
{
  "binding": {
    "config": {
      "path": "view.params.myParam"
    },
    "type": "property"
  }
}
```

**Validation:**
- [ ] `type` is `"property"`
- [ ] `config.path` exists and is non-empty
- [ ] Property path format is valid (contains at least one dot, no leading/trailing dots, no double dots)

**Common Errors:**
- Empty `path`
- Invalid property path format

---

## Validation Workflow

### Step 1: Structural Validation

```java
// Validate view JSON structure
PerspectiveViewValidator validator = new PerspectiveViewValidator();
ComponentCatalog catalog = new ComponentCatalog();

ValidationResult result = validator.validate(viewJson, catalog);

if (!result.isValid()) {
    // Handle structural errors
    for (ValidationIssue error : result.getErrors()) {
        System.err.println(error.getPath() + ": " + error.getMessage());
    }
}
```

### Step 2: Dynamic Diagnostics

```java
// Validate against live gateway
DiagnosticService service = new DiagnosticService(context);

ViewDiagnostics diagnostics = service.diagnoseView(project, viewPath);

if (!diagnostics.isValid()) {
    // Handle runtime errors
    for (DiagnosticIssue error : diagnostics.getErrors()) {
        System.err.println(error.getCode() + ": " + error.getMessage());
    }
}
```

### Step 3: Binding-Specific Validation

```java
// Validate specific binding
BindingDiagnostics bindingDiag = service.diagnoseBinding(
    project, viewPath, componentPath, propertyPath
);

if (!bindingDiag.isValid()) {
    // Handle binding errors
    for (DiagnosticIssue error : bindingDiag.getErrors()) {
        System.err.println(error.getCode() + ": " + error.getMessage());
    }
}
```

---

## Error Handling

### ValidationIssue

```java
public record ValidationIssue(
    String path,           // JSON path to issue
    String code,           // Error code
    String severity,       // ERROR or WARNING
    String message         // Human-readable message
) {}
```

### DiagnosticIssue

```java
public record DiagnosticIssue(
    String code,           // Error code
    String severity,       // ERROR or WARNING
    String category,       // VIEW, COMPONENT, BINDING
    String message,        // Human-readable message
    String path            // Component/binding path
) {}
```

---

## Common Validation Patterns

### Pattern: Validate All Bindings

```java
public List<DiagnosticIssue> validateAllBindings(
    String project, String viewPath, JsonObject view
) {
    List<DiagnosticIssue> issues = new ArrayList<>();
    
    // Traverse view structure
    traverseComponent(view, path -> {
        // Check each binding property
        for (String property : getBindingProperties(path)) {
            BindingDiagnostics diag = diagnosticService.diagnoseBinding(
                project, viewPath, path, property
            );
            issues.addAll(diag.getErrors());
        }
    });
    
    return issues;
}
```

### Pattern: Validate runScript() Calls

```java
public List<DiagnosticIssue> validateRunScriptCalls(JsonObject view) {
    List<DiagnosticIssue> issues = new ArrayList<>();
    
    // Find all expression bindings
    findExpressionBindings(view, binding -> {
        String expression = binding.getString("expression");
        
        // Check for runScript() calls
        if (expression.contains("runScript(")) {
            // Validate module path
            String modulePath = extractModulePath(expression);
            if (!isValidModulePath(modulePath)) {
                issues.add(new DiagnosticIssue(
                    "INVALID_RUNSCRIPT_PATH",
                    "ERROR",
                    "BINDING",
                    "Invalid runScript() path: " + modulePath,
                    getCurrentPath()
                ));
            }
        }
    });
    
    return issues;
}
```

---

## Verified APIs (From Our Implementation)

### Static Validation
- `com.axcend.ignition.agenttools.validate.PerspectiveViewValidator` ✓
- `com.axcend.ignition.agenttools.validate.ComponentCatalog` ✓
- `com.axcend.ignition.agenttools.validate.ValidationIssue` ✓

### Dynamic Diagnostics
- `com.axcend.ignition.agenttools.diagnostic.DiagnosticService` ✓
- `com.axcend.ignition.agenttools.diagnostic.LogCaptureService` ✓
- `com.axcend.ignition.agenttools.diagnostic.ViewDiagnostics` ✓
- `com.axcend.ignition.agenttools.diagnostic.ComponentDiagnostics` ✓
- `com.axcend.ignition.agenttools.diagnostic.BindingDiagnostics` ✓
- `com.axcend.ignition.agenttools.diagnostic.DiagnosticIssue` ✓

---

## Known Limitations

### Public SDK Limitations

The public SDK does NOT expose:
- `getAllBindingErrors()` API
- Direct binding evaluation API
- Complete diagnostic API

### Our Workaround

We implemented:
- `PerspectiveViewValidator` — Static structural validation
- `DiagnosticService` — Dynamic runtime diagnostics
- `LogCaptureService` — Gateway log capture

---

## Common Mistakes

| Mistake | Correct Approach |
|---------|------------------|
| Implementing expression parser | Use Ignition's expression infrastructure |
| Copying 8.1 validation | Verify against 8.3.7 SDK |
| Assuming `validate()` method exists | Check actual SDK API |
| Ignoring warnings | Investigate all warnings |

---

*This skill provides binding validation patterns for Ignition 8.3.7. All patterns are proven through our implementation.*
