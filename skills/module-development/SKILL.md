---
name: module-development
description: Ignition 8.3.7 module development patterns. Covers gateway hooks, route registration, script execution, and module lifecycle. Load when creating or modifying Ignition modules.
---

# Ignition 8.3.7 Module Development

## Purpose

This skill provides verified patterns for developing Ignition 8.3.7 gateway modules. All patterns are proven through our `ai-agent-tools` module implementation.

**Source of truth:** Official Ignition 8.3.7 SDK Javadocs + our verified implementation

---

## Module Structure

### Standard Directory Layout

```
my-module/
├── build.gradle              # Root build file
├── settings.gradle           # Project settings
├── gradle.properties         # Version, group, etc.
├── gradlew / gradlew.bat    # Gradle wrapper
├── gateway/                  # Gateway subproject
│   ├── build.gradle
│   └── src/main/java/...
└── common/                   # Common subproject (optional)
    ├── build.gradle
    └── src/main/java/...
```

### build.gradle (Root)

```groovy
plugins {
    id 'io.ia.sdk.modl' version '0.1.1'
}

ext {
    ignitionVersion = '8.3.7'
}

subprojects {
    apply plugin: 'java-library'
    
    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }
    
    repositories {
        maven { url 'https://nexus.inductiveautomation.com/repository/public' }
        mavenCentral()
    }
    
    dependencies {
        compileOnly "com.inductiveautomation.ignitionsdk:gateway-api:${rootProject.ext.ignitionVersion}"
        compileOnly "com.inductiveautomation.ignitionsdk:ignition-common:${rootProject.ext.ignitionVersion}"
    }
}
```

### gradle.properties

```properties
group=com.example
version=0.1.0-SNAPSHOT
ignitionVersion=8.3.7
moduleVendor=MyCompany
```

---

## Gateway Hook Lifecycle

### AbstractGatewayModuleHook

```java
public class MyGatewayHook extends AbstractGatewayModuleHook {
    
    private GatewayContext context;
    private MyService service;
    
    @Override
    public void setup(GatewayContext context) {
        // Called once when module is loaded
        // Construct services here
        this.context = context;
        this.service = new MyService(context);
    }
    
    @Override
    public void startup(LicenseState activationState) {
        // Called when module is activated
        // No-op for most modules
    }
    
    @Override
    public void shutdown() {
        // Called when module is unloaded
        // Clean up resources
        this.service = null;
        this.context = null;
    }
    
    @Override
    public void mountRouteHandlers(RouteGroup routes) {
        // Register REST endpoints here
    }
}
```

### Lifecycle Order

```
Module loaded
    │
    ▼
setup(GatewayContext) ← Construct services
    │
    ▼
startup(LicenseState) ← Activate
    │
    ▼
mountRouteHandlers(RouteGroup) ← Register routes
    │
    ▼
[Module running]
    │
    ▼
shutdown() ← Clean up
```

---

## Route Registration

### Pattern

```java
@Override
public void mountRouteHandlers(RouteGroup routes) {
    routes.newRoute("/health")
          .type(RouteGroup.TYPE_JSON)
          .handler(this::health)
          .method(HttpMethod.GET)
          .accessControl(AccessControlStrategy.OPEN_ROUTE)
          .mount();
    
    routes.newRoute("/data")
          .type(RouteGroup.TYPE_JSON)
          .handler(this::getData)
          .method(HttpMethod.POST)
          .accessControl(AccessControlStrategy.REQUIRE_SESSION)
          .mount();
}
```

### RouteGroup Methods

| Method | Description |
|--------|-------------|
| `newRoute(String)` | Create new route |
| `type(String)` | Set content type (`TYPE_JSON`, etc.) |
| `handler(RouteHandler)` | Set handler method |
| `method(HttpMethod)` | Set HTTP method |
| `accessControl(AccessControlStrategy)` | Set access control |
| `mount()` | Register the route |

### AccessControlStrategy

| Strategy | Description |
|----------|-------------|
| `OPEN_ROUTE` | No authentication required |
| `REQUIRE_SESSION` | Require valid gateway session |
| `REQUIRE_PROJECT` | Require project access |

---

## Handler Pattern

### Basic Handler

```java
private void health(RoutingContext ctx) {
    JsonObject response = new JsonObject();
    response.put("status", "ok");
    response.put("version", "1.0.0");
    
    ctx.response()
       .putHeader("Content-Type", "application/json")
       .end(response.encode());
}
```

### Handler with Request Body

```java
private void processData(RoutingContext ctx) {
    ctx.bodyHandler(buffer -> {
        JsonObject request = buffer.toJson();
        
        // Process request
        String input = request.getString("input");
        JsonObject result = processInput(input);
        
        // Send response
        ctx.response()
           .putHeader("Content-Type", "application/json")
           .end(result.encode());
    });
}
```

### Handler with Query Parameters

```java
private void getData(RoutingContext ctx) {
    String project = ctx.queryParam("project");
    String view = ctx.queryParam("view");
    
    // Process query params
    JsonObject result = fetchData(project, view);
    
    ctx.response()
       .putHeader("Content-Type", "application/json")
       .end(result.encode());
}
```

---

## Script Execution

### GatewayScriptService Pattern

```java
public class GatewayScriptService {
    
    private final ScriptManager scriptManager;
    private final ExecutorService executor;
    
    public GatewayScriptService(GatewayContext context) {
        this.scriptManager = context.getScriptManager();
        this.executor = Executors.newCachedThreadPool();
    }
    
    public ScriptResult executeScript(String code, int timeoutSec) {
        Future<ScriptResult> future = executor.submit(() -> {
            return scriptManager.runScript(
                "com.example.script",
                code,
                timeoutSec * 1000
            );
        });
        
        try {
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ScriptTimeoutException("Script timed out");
        }
    }
}
```

### Available system.* Functions

In gateway scope, these are available:
- `system.tag.read(tagPath)` — Read tag value
- `system.tag.write(tagPath, value)` — Write tag value
- `system.tag.browse(tagPath)` — Browse tags
- `system.db.runNamedQuery(queryPath, params)` — Run named query
- `system.util.getLogger(name)` — Get logger

**NOT available in gateway scope:**
- `system.perspective` — Client-scope only
- `system.gui` — Client-scope only
- `system.nav` — Client-scope only

---

## Module Deployment

### deploy.ps1 Pattern

```powershell
# 1. Build
.\gradlew.bat clean build

# 2. Stop gateway
& "C:\Program Files\Inductive Automation\Ignition\stop-ignition.bat"

# 3. Register module
$modulesJson = "C:\Program Files\Inductive Automation\Ignition\data\modules.json"
$modules = Get-Content $modulesJson | ConvertFrom-Json
$modules += @{
    id = "com.example.mymodule"
    name = "My Module"
    version = "0.1.0"
    scope = "G"
    path = "modules\ai-agent-tools.unsigned.modl"
}
$modules | ConvertTo-Json | Set-Content $modulesJson

# 4. Copy .modl file
Copy-Item "build\ai-agent-tools.unsigned.modl" "C:\Program Files\Inductive Automation\Ignition\data\modules\"

# 5. Start gateway
& "C:\Program Files\Inductive Automation\Ignition\start-ignition.bat"

# 6. Verify
Start-Sleep -Seconds 30
Invoke-RestMethod "http://localhost:8088/data/agent-tools/health"
```

---

## Testing

### Unit Tests (JUnit 5)

```java
@Test
void testValidation() {
    PerspectiveViewValidator validator = new PerspectiveViewValidator();
    ComponentCatalog catalog = new ComponentCatalog();
    
    String viewJson = "{\"root\":{\"type\":\"ia.container.flex\"}}";
    ValidationResult result = validator.validate(viewJson, catalog);
    
    assertTrue(result.isValid());
    assertEquals(0, result.getErrors().size());
}
```

### Integration Tests

```java
@Test
void testEndpoint() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8088/data/agent-tools/health"))
        .GET()
        .build();
    
    HttpResponse<String> response = client.send(
        request, 
        HttpResponse.BodyHandlers.ofString()
    );
    
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("ok"));
}
```

---

## Common Mistakes

| Mistake | Correct Approach |
|---------|------------------|
| Using JDK 8 syntax | Use JDK 17 features |
| Using `compile` instead of `compileOnly` | SDK is provided at runtime |
| Forgetting `mount()` | Always call `.mount()` after route setup |
| Not handling exceptions | Always wrap handlers in try-catch |
| Using client-scope functions | Only gateway-scope functions available |

---

## Verified APIs (From Our Implementation)

### Gateway Hook
- `com.inductiveautomation.ignition.common.gateway.AbstractGatewayModuleHook` ✓
- `com.inductiveautomation.ignition.common.gateway.GatewayContext` ✓
- `com.inductiveautomation.ignition.common.gateway.LicenseState` ✓
- `com.inductiveautomation.ignition.common.gateway.RouteGroup` ✓

### Script Execution
- `com.inductiveautomation.ignition.common.scripting.ScriptManager` ✓

### HTTP
- `io.vertx.core.http.HttpMethod` ✓
- `io.vertx.core.http.HttpServerRequest` ✓
- `io.vertx.core.http.HttpServerResponse` ✓
- `io.vertx.core.json.JsonObject` ✓

---

## Official Resources

| Resource | URL |
|----------|-----|
| SDK Examples | https://github.com/inductiveautomation/ignition-sdk-examples (branch: ignition-8.3) |
| SDK Javadocs | https://sdk.inductiveautomation.com/javadoc/ignition83/8.3.7/ |

---

*This skill provides module development patterns for Ignition 8.3.7. All patterns are proven through our implementation.*
