# Changelog — Ignition Agent Skills

## August 2026

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
