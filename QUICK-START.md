# Quick Start — Ignition Agent Skills

## For AI Agents

**Always follow this order:**

```
Step 1: Load ignition-conventions (patterns, decision trees)
Step 2: Load ignition-perspective-skills (component schemas)
Step 3: Load specific component skill
```

**For tag management:**
```
Step 1: Load ignition-openapi (Gateway API)
Step 2: Load ignition-tags (tag workflows)
```

## For Developers

### Where Things Live

| What | Where |
|------|-------|
| Agent instructions | `AGENTS.md` |
| Conventions & patterns | `.agents/skills/ignition-conventions/` |
| Gateway API skills | `.agents/skills/ignition-openapi/` |
| Perspective component skills | `.agents/skills/ignition-perspective-skills/` |
| Template library | `Template_Library/` |
| Your project | `<YourProjectName>/` |

### How to Add a New Skill

1. Create folder in the right parent directory
2. Create `SKILL.md` with frontmatter
3. Update the parent's `SKILL.md` router
4. Test loading via `skill` tool

### Key Rules

- **Never edit** `Template_Library/` during normal development
- **Never guess** component types, property names, or JSON schemas
- **Always load** the relevant skill first
- **Tab indentation** for script transforms
- **Jython 2.7** only (no Python 3 syntax)

---

## Full Documentation

See `SKILL-ARCHITECTURE.md` for complete reference.
