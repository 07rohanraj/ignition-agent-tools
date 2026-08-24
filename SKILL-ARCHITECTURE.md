# Ignition Agent Skills — Architecture & Reference

A complete reference for the AI skill system built for Ignition Perspective projects.

---

## Table of Contents

1. [Overview](#overview)
2. [Directory Structure](#directory-structure)
3. [Three-Layer Architecture](#three-layer-architecture)
4. [Skill Folder Reference](#skill-folder-reference)
5. [Routing Flow](#routing-flow)
6. [How Skills Load](#how-skills-load)
7. [Key Files Reference](#key-files-reference)
8. [Conventions & Patterns Created](#conventions--patterns-created)
9. [What Each Skill Does](#what-each-skill-does)
10. [Adding New Skills](#adding-new-skills)
11. [Maintenance Notes](#maintenance-notes)

---

## Overview

The skill system provides specialized instructions to AI agents working on Ignition Perspective projects. It replaces the need for agents to "watch" other projects by encoding common patterns, conventions, and component schemas into structured skill files.

**Core principle:** Never guess. Always load the relevant skill first.

---

## Directory Structure

```
C:\Program Files\Inductive Automation\Ignition\data\projects\
│
├── .agents/
│   ├── skills/
│   │   ├── ignition-conventions/                    # Layer 1: Patterns & conventions
│   │   │   ├── SKILL.md                             # Router for conventions
│   │   │   ├── ignition-project-structure/          # Project file structure reference
│   │   │   │   └── SKILL.md
│   │   │   └── common-ignition-patterns/            # 8 common pattern skills
│   │   │       ├── bindings-and-transforms/
│   │   │       │   └── SKILL.md
│   │   │       ├── scripting-conventions/
│   │   │       │   └── SKILL.md
│   │   │       ├── common-components/
│   │   │       │   └── SKILL.md
│   │   │       ├── view-layout-and-position/
│   │   │       │   └── SKILL.md
│   │   │       ├── view-structure-and-properties/
│   │   │       │   └── SKILL.md
│   │   │       ├── page-config-and-navigation/
│   │   │       │   └── SKILL.md
│   │   │       ├── styles-and-css/
│   │   │       │   └── SKILL.md
│   │   │       └── common-data-patterns/
│   │   │           └── SKILL.md
│   │   │
│   │   ├── ignition-openapi/                        # Layer 1: Gateway API skills
│   │   │   ├── SKILL.md                             # Router for API skills
│   │   │   ├── openapi.json                         # Full OpenAPI specification (12MB)
│   │   │   ├── config.example.json                  # Shared API config template
│   │   │   └── ignition-tags/                       # Tag management via REST API
│   │   │       ├── SKILL.md
│   │   │       ├── schemas/                         # 9 JSON schemas for tag types
│   │   │       │   ├── memory-tag.json
│   │   │       │   ├── opc-tag.json
│   │   │       │   ├── expression-tag.json
│   │   │       │   ├── query-tag.json
│   │   │       │   ├── reference-tag.json
│   │   │       │   ├── derived-tag.json
│   │   │       │   ├── folder.json
│   │   │       │   ├── udt-definition.json
│   │   │       │   └── udt-instance.json
│   │   │       └── scripts/
│   │   │           ├── import-tags.py
│   │   │           └── export-tags.py
│   │   │
│   │   └── ignition-perspective-skills/             # Layer 2: Perspective component schemas
│   │       ├── SKILL.md                             # Master router for all Perspective skills
│   │       ├── alarms/
│   │       ├── bindings/
│   │       ├── buttons/
│   │       ├── charts/
│   │       ├── containers/
│   │       ├── display/
│   │       ├── embedded/
│   │       ├── forms/
│   │       ├── fundamentals/
│   │       ├── gauges/
│   │       ├── industrial/
│   │       ├── input/
│   │       ├── template-library-usage/
│   │       └── transforms/
│   │
│   └── tools/
│       └── copy_template.py
│
├── AGENTS.md                                        # Agent instructions (mandatory read)
├── README.md                                        # Project overview
└── ignition-agent-tools/                            # This documentation folder
    └── SKILL-ARCHITECTURE.md                        # This file
```

---

## Three-Layer Architecture

```
                    AI AGENT
                       │
                       ▼
              AGENTS.md / Instructions
                       │
                       ▼
              MASTER SKILL ROUTERS
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
   ignition-      ignition-    ignition-
   conventions    openapi      perspective-
                                  skills
          │            │            │
          ▼            ▼            ▼
    Patterns &    Gateway API  Component
    Conventions   Discovery    Schemas
          │            │            │
          └────────────┼────────────┘
                       ▼
                 VALIDATION
```

### Layer 1: Conventions & Patterns (`ignition-conventions`)

**Purpose:** Decision trees, standard patterns, and rules that apply across all Ignition work.

**Always load first.** These skills tell the agent HOW to approach a problem before it touches any component.

Contains:
- `ignition-project-structure` — Canonical directory tree, resource.json format, REST API reference
- `common-ignition-patterns/` — 8 pattern skills covering bindings, scripting, components, layout, structure, navigation, styles, data patterns

### Layer 1: Gateway API (`ignition-openapi`)

**Purpose:** REST API discovery and tag management.

Contains:
- `openapi.json` — Full OpenAPI specification (12MB)
- `config.example.json` — Shared API configuration (host, token, provider)
- `ignition-tags/` — Tag creation, import, export via Gateway REST API

### Layer 2: Component Schemas (`ignition-perspective-skills`)

**Purpose:** Authoritative JSON schemas, property definitions, and default values for every Perspective component.

Contains:
- `SKILL.md` — Master routing table
- 15 category folders with component-specific skills
- `template-library-usage/` — Mandatory template lookup and copy protocol

---

## Skill Folder Reference

### `ignition-conventions`

| Skill | Purpose | Lines |
|-------|---------|-------|
| `SKILL.md` | Router — lists all convention skills | ~70 |
| `ignition-project-structure/SKILL.md` | Project directory tree, resource.json, REST API | ~943 |
| `common-ignition-patterns/bindings-and-transforms/SKILL.md` | Binding decision tree, transform patterns | ~300 |
| `common-ignition-patterns/scripting-conventions/SKILL.md` | Jython 2.7 patterns, system.* API | ~250 |
| `common-ignition-patterns/common-components/SKILL.md` | Top 10 components with typical props | ~250 |
| `common-ignition-patterns/view-layout-and-position/SKILL.md` | Flex sizing, scrollbar prevention | ~200 |
| `common-ignition-patterns/view-structure-and-properties/SKILL.md` | view.json anatomy, propConfig, params | ~250 |
| `common-ignition-patterns/page-config-and-navigation/SKILL.md` | Routes, nav menus, docks, mobile | ~200 |
| `common-ignition-patterns/styles-and-css/SKILL.md` | CSS variables, classes, responsive | ~200 |
| `common-ignition-patterns/common-data-patterns/SKILL.md` | Dataset/JSON transforms, sqlType values | ~200 |

### `ignition-openapi`

| Skill | Purpose | Lines |
|-------|---------|-------|
| `SKILL.md` | Router — API discovery + tags sub-skill | ~70 |
| `openapi.json` | Full OpenAPI specification | ~28K lines |
| `config.example.json` | Gateway host/token/provider template | 5 |
| `ignition-tags/SKILL.md` | Tag create/import/export workflow | ~694 |
| `ignition-tags/schemas/*.json` | JSON schemas for 9 tag types | varies |
| `ignition-tags/scripts/*.py` | Import/export Python scripts | varies |

### `ignition-perspective-skills`

| Skill | Purpose |
|-------|---------|
| `SKILL.md` | Master router for all Perspective component skills |
| `alarms/` | Alarm Status Table, Alarm Journal Table |
| `bindings/` | HTTP, Session Properties, Expression Structure + 5 individual binding skills |
| `buttons/` | Horizontal Menu, Link, Multi-State Button, One-Shot Button, Button |
| `charts/` | Time Series, Power Chart, XY Chart + Pie, Sparkline, Chart Range |
| `containers/` | Flex, Column, Accordion, Breakpoint, Carousel, Coordinate, Dashboard, Split, Tab, View Canvas |
| `display/` | Label, Table, Icon + Audio, Barcode, File Upload, Image, Inline Frame, LED, Markdown, Menu Tree, PDF, Signature, Tag Browse Tree, Tree, Video |
| `embedded/` | Embedded View, Flex Repeater |
| `forms/` | Form Configuration, Equipment Schedule |
| `fundamentals/` | Build View, Default Configs, Component Meta, CSS Properties, Container Position, Named Query, Docks |
| `gauges/` | Gauge, Simple Gauge, Thermometer, Linear Scale, Moving Analog, Progress |
| `industrial/` | Motor, Pump, Valve, Vessel, Sensor Symbol, Cylindrical Tank |
| `input/` | Text Field, Text Area, Numeric Entry, Dropdown, DateTime Picker, Checkbox + Barcode, Password, Radio, Slider, Toggle |
| `template-library-usage/` | Mandatory template lookup & copy protocol |
| `transforms/` | Expression, Script, Format, Map transforms |

---

## Routing Flow

### For Perspective Component Tasks

```
1. Load ignition-conventions (always first)
   └─ Provides: decision trees, patterns, common mistakes

2. Load template-library-usage (if building charts/gauges/tables/dashboards)
   └─ Enforces: registry lookup, copy-before-build

3. Load ignition-perspective-skills (master router)
   └─ Provides: which component skill to load

4. Load specific component skill (e.g., perspective-flex-container)
   └─ Provides: exact JSON schema, properties, defaults
```

### For Gateway API Tasks

```
1. Load ignition-openapi (router)
   └─ Provides: API discovery, openapi.json reference

2. Load ignition-tags (if working with tags)
   └─ Provides: tag schemas, import/export scripts
```

### For Project Structure Tasks

```
1. Load ignition-conventions (router)
   └─ Provides: which skill to load

2. Load ignition-project-structure
   └─ Provides: directory tree, resource.json format, naming conventions
```

---

## How Skills Load

Skills are loaded via the `skill` tool in the AI agent. Each skill has a `SKILL.md` file with frontmatter:

```yaml
---
name: skill-name
description: When to use this skill
---
```

The agent uses the `skill` tool to load a skill by name, which injects the skill's content into the conversation. The agent then follows the instructions in that skill.

### Skill Content Format

Each skill follows this structure:

```markdown
# Skill Name

## When to Use
- Condition 1
- Condition 2

## Standard Pattern
[JSON examples, code patterns]

## Rules
1. Rule 1
2. Rule 2

## Common Mistakes
| Mistake | Fix |
|---------|-----|

## Validation
1. Check 1
2. Check 2
```

---

## Key Files Reference

### Root Level

| File | Purpose |
|------|---------|
| `AGENTS.md` | Mandatory agent instructions — always read first |
| `README.md` | Project overview, template workflow |

### `.agents/skills/` Level

| File | Purpose |
|------|---------|
| `ignition-conventions/SKILL.md` | Router for conventions/patterns |
| `ignition-openapi/SKILL.md` | Router for Gateway API skills |
| `ignition-perspective-skills/SKILL.md` | Master router for Perspective components |

### `ignition-openapi/` Level

| File | Purpose |
|------|---------|
| `config.example.json` | Gateway API config template (host, token, provider) |
| `openapi.json` | Full OpenAPI specification for Gateway REST API |

### `ignition-openapi/ignition-tags/` Level

| File | Purpose |
|------|---------|
| `SKILL.md` | Tag management workflow and reference |
| `schemas/*.json` | JSON schemas for tag validation |
| `scripts/import-tags.py` | Import tags to Gateway |
| `scripts/export-tags.py` | Export tags from Gateway |

---

## Conventions & Patterns Created

### `bindings-and-transforms`

**Decision tree:**
- Tag data → Tag Binding
- Named Query → Query Binding
- Another property → Property Binding
- Compute from values → Expression Binding
- REST API → HTTP Binding
- Session/user info → Session Properties
- Python processing → Script Transform
- Formatting → Format Transform
- Value remapping → Map Transform

**Key rule:** Tab indentation (`\t`) for script transforms.

### `scripting-conventions`

**Key rules:**
- Jython 2.7 (not Python 3)
- No f-strings — use `"%s" % (var)`
- Tab indentation in script transforms
- Always `return` for output (not `print()`)

### `common-components`

**Top 10 components:**
1. `ia.container.flex` — Layout
2. `ia.display.label` — Text display
3. `ia.input.button` — Actions
4. `ia.input.dropdown` — Selection
5. `ia.input.text-field` — Text input
6. `ia.input.numeric-field` — Number input
7. `ia.input.date-time-picker` — Date/time
8. `ia.input.checkbox` — Boolean
9. `ia.display.table` — Tabular data
10. `ia.display.icon` — Icons

### `view-layout-and-position`

**Scrollbar prevention:**
```json
{
  "position": {
    "grow": 1,
    "shrink": 1,
    "basis": "0"
  }
}
```
Parent: `style.overflow: "hidden"`

### `common-data-patterns`

**sqlType values (Ignition 8.3):**
| Value | Type |
|-------|------|
| 2 | Int4 |
| 3 | Int8 |
| 4 | Float4 |
| 5 | Float8 |
| 6 | Boolean |
| 7 | DateTime |
| 8 | String |
| 20 | ByteArray |

**Script-python rule:** Only leaf files contain code. Namespace directories are always empty.

---

## Adding New Skills

### To `ignition-conventions/common-ignition-patterns/`

1. Create directory: `.agents/skills/ignition-conventions/common-ignition-patterns/new-skill-name/`
2. Create `SKILL.md` with frontmatter and content
3. Update `ignition-conventions/SKILL.md` to add routing entry
4. Update Quick Task Reference in `ignition-conventions/SKILL.md`

### To `ignition-perspective-skills/`

1. Create directory: `.agents/skills/ignition-perspective-skills/category/new-skill-name/`
2. Create `SKILL.md` with component schema, properties, and defaults
3. Update `ignition-perspective-skills/SKILL.md` to add routing entry

### To `ignition-openapi/`

1. Create subdirectory: `.agents/skills/ignition-openapi/new-skill-name/`
2. Create `SKILL.md` with API workflow and reference
3. Update `ignition-openapi/SKILL.md` to add routing entry

---

## Maintenance Notes

### What NOT to Edit

- `openapi.json` — Generated from Gateway, do not hand-edit
- Component schemas in `ignition-perspective-skills/*/SKILL.md` — Should match official Ignition component schemas

### What CAN Be Edited

- Convention/pattern skills — Add new patterns as they're discovered
- Routing tables in `SKILL.md` files — Update when adding/removing skills
- `AGENTS.md` and `README.md` — Update when structure changes

### File Naming Conventions

- Skill directories: `kebab-case` (e.g., `bindings-and-transforms`)
- Skill files: Always `SKILL.md`
- Schema files: `kebab-case.json` (e.g., `memory-tag.json`)
- Scripts: `kebab-case.py` (e.g., `import-tags.py`

### Testing

After modifying skills:
1. Verify all `SKILL.md` files have valid frontmatter
2. Check that routing tables reference existing skills
3. Test skill loading via the `skill` tool
4. Verify JSON schemas are valid

---

## Statistics

| Category | Count |
|----------|-------|
| Skill folders | 3 |
| Router SKILL.md files | 3 |
| Pattern skills | 8 |
| Component categories | 15 |
| Component skills | ~50 |
| Tag schemas | 9 |
| Total SKILL.md files | ~70 |

---

*Last updated: August 2026*
