# Ignition API Rules

**Target version:** 8.3.7

This document defines the project's contract with the Ignition SDK. It is NOT a skill — it is a set of strict rules that MUST be followed when using any Ignition API.

---

## Source Priority

When researching Ignition APIs, use these sources in order:

1. **Ignition 8.3.7 SDK Javadocs** — Authoritative API reference
2. **Official Ignition 8.3 SDK examples** — Verified working code
3. **Official Ignition documentation** — User guides and manuals
4. **Ignition forum/community** — Community solutions (verify compatibility)
5. **Other sources** — Last resort, verify independently

---

## Forbidden Assumptions

Never assume ANY of the following:

| Assumption | Why It's Forbidden |
|------------|-------------------|
| 8.1 API == 8.3 API | APIs change between major versions |
| Internal class == supported SDK API | Internal classes may be removed without notice |
| Forum example works in 8.3.7 | Examples may be outdated or version-specific |
| Method exists because similar name exists | Method signatures must be exact |
| Class name from memory is evidence | Always verify in SDK Javadocs |
| Deprecated API still works | Deprecated APIs may be removed |
| Example from docs is current | Documentation may lag behind releases |

---

## Before Using an API

### Verification Checklist

Before using ANY Ignition API, verify ALL of the following:

- [ ] **Class exists** in 8.3.7 SDK
- [ ] **Package is correct** (full qualified name)
- [ ] **Method name is exact** (case-sensitive)
- [ ] **Parameters match** (types, order, count)
- [ ] **Return type is correct**
- [ ] **Visibility is accessible** (public/protected)
- [ ] **Version compatibility confirmed**

### Verification Process

```
Agent wants to use Ignition API
          │
          ▼
Find candidate class in SDK knowledge
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
              Verify version compatibility
                   │
                   ▼
                 Code
```

---

## API Usage Rules

### Rule 1: Always Verify First

```java
// WRONG: Assuming API exists
ExpressionBindingFactory factory = new ExpressionBindingFactory();

// RIGHT: Verify in SDK first
// Check: Does ExpressionBindingFactory exist in 8.3.7?
// Check: Is it public?
// Check: What are its methods?
ExpressionBindingFactory factory = // ... verified API
```

### Rule 2: Never Copy Without Verification

```java
// WRONG: Copying from 8.1 example
// Found in forum: "Use BindingContext.create()"

// RIGHT: Verify in 8.3.7 SDK
// Check: Does BindingContext.create() exist?
// Check: What are its parameters?
BindingContext ctx = // ... verified API
```

### Rule 3: Use Official Examples

```java
// WRONG: Using unverified code from internet
// Found on blog: someUnverifiedCode()

// RIGHT: Use official SDK examples
// Source: https://github.com/inductiveautomation/ignition-sdk-examples
// Branch: ignition-8.3
```

### Rule 4: Document Verified APIs

When you verify an API works, document it:

```markdown
## Verified APIs

### Expression Binding
- `com.inductiveautomation.perspective.gateway.binding.expression.ExpressionBinding` ✓
  - Verified: 2026-08-27
  - Used in: binding-validation skill
  - Works with: Ignition 8.3.7
```

### Rule 5: Version-Specific Code

```java
// WRONG: Ignoring version
// Code that works in 8.1 may not work in 8.3

// RIGHT: Version-aware code
// Always target 8.3.7
// Use JDK 17 features
// Use current SDK APIs
```

---

## Dependency Rules

### Required Dependencies

```groovy
// Gateway module dependencies
compileOnly "com.inductiveautomation.ignitionsdk:gateway-api:8.3.7"
compileOnly "com.inductiveautomation.ignitionsdk:ignition-common:8.3.7"
```

### Forbidden Dependencies

| Dependency | Why |
|------------|-----|
| Old SDK versions | Must target 8.3.7 |
| Internal Ignition classes | Not supported |
| Third-party Ignition modules | May conflict |

---

## Code Style Rules

### JDK Version

- **Required:** JDK 17
- **Forbidden:** JDK 8 syntax, JDK 11 features

### Jython Version

- **Required:** Jython 2.7
- **Forbidden:** Python 3 syntax (f-strings, type hints, etc.)

### Module Plugin

- **Required:** `io.ia.sdk.modl` version 0.1.1
- **Forbidden:** Older plugin versions

---

## Testing Rules

### Unit Tests

- Use JUnit 5
- Test pure logic without gateway
- Verify API usage in isolation

### Integration Tests

- Test against running gateway
- Verify endpoints work
- Test error handling

### Validation Tests

- Test view validation
- Test binding validation
- Test diagnostic services

---

## Documentation Rules

### When to Document

- After verifying an API works
- After discovering a version-specific behavior
- After solving a complex problem
- After finding a workaround

### What to Document

- API class and method signatures
- Version compatibility notes
- Known limitations
- Common mistakes
- Working examples

### Where to Document

- Skills: Task-specific instructions
- Patterns: Verified implementation patterns
- API_RULES.md: This file (project-wide rules)

---

## Common Mistakes

| Mistake | Correct Approach |
|---------|------------------|
| Using 8.1 API in 8.3.7 | Verify against 8.3.7 SDK |
| Copying forum code | Use official SDK examples |
| Assuming internal class works | Use supported SDK API |
| Ignoring version | Always target 8.3.7 |
| Not documenting verified APIs | Document after verification |
| Using Python 3 syntax | Use Jython 2.7 |
| Using old module plugin | Use `io.ia.sdk.modl` 0.1.1 |

---

## Verification Sources

| Source | URL | Trust Level |
|--------|-----|-------------|
| SDK Javadocs | https://sdk.inductiveautomation.com/javadoc/ignition83/8.3.7/ | Highest |
| SDK Examples | https://github.com/inductiveautomation/ignition-sdk-examples | High |
| Official Docs | https://docs.inductiveautomation.com/ | High |
| Forum | https://forums.inductiveautomation.com/ | Medium |
| Community blogs | Various | Low |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-08-27 | Initial rules for Ignition 8.3.7 |
| 1.1 | 2026-08-30 | Added verified version-specific behavior: native `JsonSchema.validate` silently skips `urn:ignition-schema:` refs on factory-built schemas (`RefValidator` only checks `getSubSchema()`, which is null); Perspective component `props` schemas ship in `perspective-common` `*.components.json` |

---

*This document is the project's contract with the Ignition SDK. All API usage must comply with these rules.*
