package com.axcend.ignition.agenttools;

import com.axcend.ignition.agenttools.validate.PerspectiveViewValidator;
import com.axcend.ignition.agenttools.validate.PerspectiveViewValidator.ValidationResult;
import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParseException;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import com.inductiveautomation.ignition.gateway.dataroutes.PermissionType;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteAccess;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.web.session.WebUiSession;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentToolsRouteHandlers {

    private static final Logger logger = LoggerFactory.getLogger(AgentToolsRouteHandlers.class);

    public static final String MODULE_ID = "com.axcend.ignition.agenttools";
    public static final String MODULE_VERSION = "0.1.0";

    private final GatewayContext gatewayContext;
    private final Gson gson = new Gson();
    private final PerspectiveViewValidator viewValidator = new PerspectiveViewValidator();
    private final GatewayScriptService scriptService;
    private final GatewayIntrospectionService introspectionService;

    public AgentToolsRouteHandlers(GatewayContext gatewayContext) {
        this.gatewayContext = gatewayContext;
        this.scriptService = new GatewayScriptService(gatewayContext);
        this.introspectionService = new GatewayIntrospectionService(gatewayContext);
    }

    public void shutdownServices() {
        scriptService.shutdown();
    }

    public Object health(RequestContext requestContext, HttpServletResponse response) {
        logger.info("Agent Tools health requested.");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ok");
        payload.put("module", MODULE_ID);
        payload.put("version", MODULE_VERSION);
        payload.put("capabilities", List.of(
                "view.validate",
                "script.exec",
                "gateway.info",
                "tags.browse",
                "tags.read",
                "query.run",
                "projects.resources"
        ));
        return json(payload);
    }

    public Object validateView(RequestContext requestContext, HttpServletResponse response) {
        Object denial = requireGatewayPermission(requestContext, response, PermissionType.READ);
        if (denial != null) {
            return denial;
        }

        try {
            JsonObject request = JsonParser.parseString(requestContext.readBody()).getAsJsonObject();
            JsonElement viewJson;
            if (request.has("viewJson")) {
                viewJson = request.get("viewJson");
            } else if (request.has("viewJsonString")) {
                viewJson = JsonParser.parseString(request.get("viewJsonString").getAsString());
            } else if (request.has("filePath")) {
                Path path = Paths.get(request.get("filePath").getAsString());
                if (!Files.isRegularFile(path)) {
                    return error(response, HttpServletResponse.SC_NOT_FOUND, "File not found: " + path);
                }
                viewJson = JsonParser.parseString(Files.readString(path));
            } else {
                return error(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Provide one of 'viewJson' (object), 'viewJsonString', or 'filePath'.");
            }

            ValidationResult result = viewValidator.validate(viewJson);
            return json(result.toMap());
        } catch (JsonParseException exception) {
            Map<String, Object> parseError = new LinkedHashMap<>();
            parseError.put("valid", false);
            parseError.put("errors", List.of(Map.of(
                    "path", "$",
                    "code", "PARSE_ERROR",
                    "severity", "ERROR",
                    "message", String.valueOf(exception.getMessage())
            )));
            parseError.put("warnings", List.of());
            parseError.put("stats", Map.of("componentCount", 0, "maxDepth", 0, "bindingCount", 0));
            return json(parseError);
        } catch (Exception exception) {
            logger.warn("View validation failed", exception);
            return error(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        }
    }

    public Object execScript(RequestContext requestContext, HttpServletResponse response) {
        // Dev-open by design (v1 decision): external AI agents call this over plain HTTP without a
        // gateway web session, so WebUiSession.SESSION_WRITE would always 403 them. The route is
        // network-exposed intentionally in this development setup; every execution is logged below.
        try {
            JsonObject request = JsonParser.parseString(requestContext.readBody()).getAsJsonObject();
            logger.info("Script exec requested. codeChars={}", request.has("code")
                    ? request.get("code").getAsString().length() : -1);
            Map<String, Object> result = scriptService.exec(request);
            // Always 200: a script that ran but failed is a valid outcome - check 'success' in the payload.
            response.setStatus(HttpServletResponse.SC_OK);
            return json(result);
        } catch (JsonParseException exception) {
            return error(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Request body must be JSON: " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return error(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (Exception exception) {
            logger.warn("Script execution failed", exception);
            return error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, exception.getMessage());
        }
    }

    // --- introspection ---------------------------------------------------------------------------

    public Object gatewayInfo(RequestContext requestContext, HttpServletResponse response) {
        Object denial = requireGatewayPermission(requestContext, response, PermissionType.READ);
        if (denial != null) {
            return denial;
        }
        return json(introspectionService.info());
    }

    public Object tagProviders(RequestContext requestContext, HttpServletResponse response) {
        Object denial = requireGatewayPermission(requestContext, response, PermissionType.READ);
        if (denial != null) {
            return denial;
        }
        List<String> providers = introspectionService.tagProviders();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", providers.size());
        payload.put("providers", providers);
        return json(payload);
    }

    public Object browseTags(RequestContext requestContext, HttpServletResponse response) {
        Object denial = requireGatewayPermission(requestContext, response, PermissionType.READ);
        if (denial != null) {
            return denial;
        }
        try {
            JsonObject request = JsonParser.parseString(requestContext.readBody()).getAsJsonObject();
            String path = optString(request, "path");
            boolean recursive = Boolean.TRUE.equals(optBoolean(request, "recursive"));
            Integer maxResults = optInt(request, "maxResults");
            return json(introspectionService.browseTags(path, recursive, maxResults));
        } catch (IllegalArgumentException exception) {
            return error(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (Exception exception) {
            logger.warn("Tag browse failed", exception);
            return error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, String.valueOf(exception.getMessage()));
        }
    }

    public Object readTags(RequestContext requestContext, HttpServletResponse response) {
        Object denial = requireGatewayPermission(requestContext, response, PermissionType.READ);
        if (denial != null) {
            return denial;
        }
        try {
            JsonObject request = JsonParser.parseString(requestContext.readBody()).getAsJsonObject();
            if (!request.has("paths") || !request.get("paths").isJsonArray()) {
                return error(response, HttpServletResponse.SC_BAD_REQUEST,
                        "'paths' must be an array of tag path strings.");
            }
            List<String> paths = new ArrayList<>();
            for (JsonElement element : request.getAsJsonArray("paths")) {
                // Non-primitives become null and are reported through invalidPaths.
                paths.add(element.isJsonPrimitive() ? element.getAsString() : null);
            }
            return json(introspectionService.readTags(paths));
        } catch (IllegalArgumentException exception) {
            return error(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (Exception exception) {
            logger.warn("Tag read failed", exception);
            return error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, String.valueOf(exception.getMessage()));
        }
    }

    public Object runQuery(RequestContext requestContext, HttpServletResponse response) {
        // Dev-open by design (v1 decision), same as /script/exec: agents call this without a
        // gateway web session. Every execution is logged.
        try {
            JsonObject request = JsonParser.parseString(requestContext.readBody()).getAsJsonObject();
            String queryPath = optString(request, "queryPath");
            String project = optString(request, "project");
            JsonObject parameters = request.has("parameters") && request.get("parameters").isJsonObject()
                    ? request.getAsJsonObject("parameters") : null;
            logger.info("Named query run requested. queryPath={} project={}", queryPath, project);
            return json(introspectionService.runNamedQuery(project, queryPath, parameters));
        } catch (IllegalArgumentException exception) {
            return error(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (Exception exception) {
            logger.warn("Named query run failed", exception);
            return error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, String.valueOf(exception.getMessage()));
        }
    }

    public Object listProjects(RequestContext requestContext, HttpServletResponse response) {
        Object denial = requireGatewayPermission(requestContext, response, PermissionType.READ);
        if (denial != null) {
            return denial;
        }
        List<String> names = introspectionService.projectNames();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", names.size());
        payload.put("projects", names);
        return json(payload);
    }

    public Object projectResources(RequestContext requestContext, HttpServletResponse response) {
        Object denial = requireGatewayPermission(requestContext, response, PermissionType.READ);
        if (denial != null) {
            return denial;
        }
        try {
            JsonObject request = JsonParser.parseString(requestContext.readBody()).getAsJsonObject();
            String project = optString(request, "project");
            String filter = optString(request, "contains");
            Integer maxResults = optInt(request, "maxResults");
            return json(introspectionService.listResources(project, filter, maxResults));
        } catch (IllegalArgumentException exception) {
            return error(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (Exception exception) {
            logger.warn("Project resource listing failed", exception);
            return error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, String.valueOf(exception.getMessage()));
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static String optString(JsonObject request, String key) {
        return request.has(key) && request.get(key).isJsonPrimitive()
                ? request.get(key).getAsString() : null;
    }

    private static Integer optInt(JsonObject request, String key) {
        return request.has(key) && request.get(key).isJsonPrimitive() && request.get(key).getAsJsonPrimitive().isNumber()
                ? Integer.valueOf(request.get(key).getAsInt()) : null;
    }

    private static Boolean optBoolean(JsonObject request, String key) {
        return request.has(key) && request.get(key).isJsonPrimitive() && request.get(key).getAsJsonPrimitive().isBoolean()
                ? Boolean.valueOf(request.get(key).getAsBoolean()) : null;
    }

    GatewayContext context() {
        return gatewayContext;
    }

    private Object requireGatewayPermission(RequestContext requestContext,
                                            HttpServletResponse response,
                                            PermissionType permissionType) {
        RouteAccess routeAccess;
        switch (permissionType) {
            case ACCESS -> routeAccess = WebUiSession.SESSION_ACCESS.canAccess(requestContext);
            case READ -> routeAccess = WebUiSession.SESSION_READ.canAccess(requestContext);
            case WRITE -> routeAccess = WebUiSession.SESSION_WRITE.canAccess(requestContext);
            default -> throw new IllegalStateException("Unsupported permission type: " + permissionType);
        }

        if (routeAccess == RouteAccess.GRANTED) {
            return null;
        }

        logger.warn("Agent Tools route permission denied. permissionType={} access={}", permissionType, routeAccess);

        if (routeAccess == RouteAccess.UNAUTHORIZED) {
            return error(response, HttpServletResponse.SC_UNAUTHORIZED,
                    permissionType == PermissionType.WRITE
                            ? "Gateway session or CSRF token missing."
                            : "Gateway session required.");
        }
        return error(response, HttpServletResponse.SC_FORBIDDEN,
                permissionType == PermissionType.WRITE
                        ? "Gateway session does not have write permission or the CSRF token was rejected."
                        : "Gateway session does not have read permission.");
    }

    private Object error(HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        return json(Map.of(
                "success", false,
                "message", message == null ? "Unknown error" : message
        ));
    }

    private String json(Object payload) {
        return gson.toJson(payload);
    }
}
