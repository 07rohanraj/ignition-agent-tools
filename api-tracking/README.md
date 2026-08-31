# API Tracking Directory

This directory records the relationship between features in `ignition-agent-tools` and the Ignition SDK APIs they use (or don't use).

## Purpose

- **Ground future work in reality** — know what's native vs. custom
- **Prevent re-invention** — before writing custom code, check if native API exists
- **Track migration opportunities** — identify custom code that could use native APIs

## Files

| File | Description |
|---|---|
| `native-apis-used.md` | Ignition-native SDK classes/methods actually used in the codebase, with file:line references |
| `custom-implementations.md` | Features built with custom code because no native API was used (or was missed) |
| `investigated-not-adopted.md` | Native APIs investigated but not adopted, with reasons (Designer-only, not on classpath, missed, etc.) |

## Update Rules

1. **Before** writing new custom validation/parsing code: check `investigated-not-adopted.md` and search the MCP
2. **After** adding a native API call: add entry to `native-apis-used.md` with class, method, purpose, file:line
3. **After** writing custom code that might have a native equivalent: add entry to `custom-implementations.md` with what was built and why
4. **After** investigating a native API and deciding not to use it: add entry to `investigated-not-adopted.md` with reason

## Quick Reference: High-Value Native APIs for This Module

| Domain | Native API | Status |
|---|---|---|
| Tag path parsing | `TagPathParser.parseSafe()` | ✅ Used |
| Tag semantic validation | `TagPathValidator` | ✅ **Adopted (2026-08-29)** — used in `DiagnosticService.checkTagPath()` |
| Expression parsing | `PerspectiveExpression.create()` | ✅ Used |
| Expression analysis | `Expression` / `ExpressionParseContext` | ❓ Investigate |
| JSON validation | `JsonSchema` / `JsonSchemaFactory` | ❌ **Missed — should adopt** |
| Structured errors | `ValidationErrors` / `ValidationException` | ❌ **Missed — should adopt** |
| Resource validation | `ResourceValidator` | ❓ Investigate |
| Named queries | `NamedQueryManager` | ✅ Used |
| Tag providers | `GatewayTagManager` / `TagProvider` | ✅ Used |

## MCP Integration

The `Ignition-JavaDoc-MCP/` server provides:
- `search_ignition_api(query, type, version="8.3.7")`
- `get_ignition_class_docs(packagePath, className, version="8.3.7")`
- `get_ignition_package_docs(packagePath, version="8.3.7")`
- `get_ignition_member_docs(packagePath, className, memberAnchor, version="8.3.7")`

**Always use `version: "8.3.7"`** — the default is `8.1.39` and APIs differ.