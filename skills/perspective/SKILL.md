---
name: perspective
description: Perspective module knowledge for Ignition 8.3.7. Covers view structure, bindings, components, and runtime behavior. Load when working with Perspective views or bindings.
---

# Perspective Module Knowledge (8.3.7)

## Purpose

This skill provides verified knowledge about the Perspective module in Ignition 8.3.7. It covers view structure, binding types, component architecture, and runtime behavior.

**Source of truth:** Official Ignition 8.3.7 SDK Javadocs + our verified implementation

---

## View Structure

### view.json Anatomy

```json
{
  "params": {},           // View parameters (input/output)
  "custom": {},           // Custom properties
  "root": {
    "type": "ia.container.flex",
    "props": {},
    "position": {},
    "style": {},
    "children": [],
    "propConfig": {},
    "events": {}
  }
}
```

### Key Concepts

| Concept | Description |
|---------|-------------|
| `params` | Input/output parameters passed to/from the view |
| `custom` | Custom properties for internal use |
| `root` | Root component of the view |
| `propConfig` | Binding configuration for properties |
| `children` | Child components (array) |

---

## Binding Types

### Expression Binding

**Use when:** Computing values from expressions

```json
{
  "type": "expr",
  "config": {
    "expression": "runScript('Module.function', 0)"
  }
}
```

**SDK APIs:**
- `com.inductiveautomation.perspective.gateway.binding.expression.ExpressionBinding`
- `com.inductiveautomation.perspective.gateway.binding.expression.ExpressionBindingFactory`

---

### Tag Binding

**Use when:** Reading/writing Ignition tags

```json
{
  "type": "tag",
  "config": {
    "tagPath": "[default]Folder/Tag",
    "mode": "read"
  }
}
```

**SDK APIs:**
- `com.inductiveautomation.perspective.gateway.binding.tag.TagBinding`
- `com.inductiveautomation.perspective.gateway.binding.tag.TagBindingConfig`

---

### Query Binding

**Use when:** Executing named queries

```json
{
  "type": "query",
  "config": {
    "queryPath": "MyFolder/MyQuery",
    "parameters": {},
    "pollRate": 0
  }
}
```

**SDK APIs:**
- `com.inductiveautomation.perspective.gateway.binding.query.QueryBinding`
- `com.inductiveautomation.perspective.gateway.binding.query.QueryBindingConfig`

---

### Property Binding

**Use when:** Linking to other component/view properties

```json
{
  "type": "property",
  "config": {
    "path": "view.params.myParam"
  }
}
```

---

## Component Architecture

### Component Type Format

```
ia.<category>.<component>
```

Examples:
- `ia.container.flex` — Flex Container
- `ia.display.label` — Label
- `ia.input.button` — Button

### Component Properties

Each component has:
- `props` — Component-specific properties
- `position` — Layout properties (in containers)
- `style` — CSS styling
- `meta` — Component metadata (name, visible, tooltip)

---

## runScript() Pattern

### Syntax

```
runScript('Module.path.functionName', pollRate)
```

### Parameters

| Parameter | Description |
|-----------|-------------|
| Module path | Dot-separated path (e.g., `Templates.Charts.BarChart.bar_dataset`) |
| pollRate | `0` = one-shot, `1000` = every 1000ms |

### Example

```json
{
  "propConfig": {
    "params.dataset": {
      "binding": {
        "config": {
          "expression": "runScript('Templates.Charts.BarChart.bar_dataset', 0)"
        },
        "type": "expr"
      }
    }
  }
}
```

### Critical Rules

1. **NEVER use Python imports** — `from Module import function` does NOT work
2. **NEVER use script transforms with `type: "property"`** — Causes errors
3. **ALWAYS use `"type": "expr"`** — Expression bindings only

---

## Template-Based Development

### Workflow

1. Copy template views from `Template_Library/`
2. Copy script-python modules from `Template_Library/`
3. Create Embedded Views in parent/Dashboard view
4. Bind with `runScript()` expression bindings
5. Customize input variables in `code.py` only

### Script-Python Module Structure

```
Templates/Charts/<Category>/<TemplateName>/
├── code.py        # Input variables + functions
└── resource.json  # hintScope: 2
```

### code.py Format

```python
# Input variables (editable)
variable1 = value1

# Functions (called by runScript())
def function_name():
    headers = ["Col1", "Col2"]
    rows = [["val1", "val2"]]
    return system.dataset.toDataSet(headers, rows)
```

---

## Runtime Behavior

### Binding Evaluation

1. Bindings evaluate when dependencies change
2. `runScript()` with pollRate `0` executes once
3. `runScript()` with pollRate `1000` executes every second
4. Tag bindings re-evaluate on tag value changes

### Dataset Format

Perspective expects datasets in this format:

```json
{
  "$": ["ds", 192, timestamp],
  "$columns": [
    {"data": [...], "name": "col1", "type": "String"},
    {"data": [...], "name": "col2", "type": "int"}
  ]
}
```

### Creating Datasets

Use `system.dataset.toDataSet(headers, rows)` in Jython:

```python
headers = ["Brand", "Sales"]
rows = [["Samsung", 10], ["Apple", 55]]
return system.dataset.toDataSet(headers, rows)
```

---

## Common Mistakes

| Mistake | Correct Approach |
|---------|------------------|
| `from Module import function` | `runScript('Module.function', 0)` |
| Script transform with `type: "property"` | Expression binding with `type: "expr"` |
| Binding individual params separately | Bind params directly |
| Editing `propConfig` in templates | Only edit input variables in `code.py` |

---

## Validation

### Static Validation (Our Implementation)

- `PerspectiveViewValidator` — Structural validation
- `ComponentCatalog` — Known component types
- `ValidationIssue` — Issue records

### Dynamic Diagnostics (Our Implementation)

- `DiagnosticService` — View/component/binding diagnostics
- `LogCaptureService` — Gateway log capture

---

## Verified APIs (From Our Implementation)

### View Validation
- `com.axcend.ignition.agenttools.validate.PerspectiveViewValidator` ✓
- `com.axcend.ignition.agenttools.validate.ComponentCatalog` ✓
- `com.axcend.ignition.agenttools.validate.ValidationIssue` ✓

### Diagnostics
- `com.axcend.ignition.agenttools.diagnostic.DiagnosticService` ✓
- `com.axcend.ignition.agenttools.diagnostic.LogCaptureService` ✓

---

*This skill provides Perspective-specific knowledge for Ignition 8.3.7. Always verify against the official SDK Javadocs.*
