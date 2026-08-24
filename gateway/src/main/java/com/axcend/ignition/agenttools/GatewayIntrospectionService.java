package com.axcend.ignition.agenttools;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.common.browsing.BrowseFilter;
import com.inductiveautomation.ignition.common.browsing.Results;
import com.inductiveautomation.ignition.common.db.namedquery.NamedQueryManager;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonPrimitive;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.resourcecollection.Resource;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceId;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.common.resourcecollection.RuntimeResourceCollection;
import com.inductiveautomation.ignition.common.tags.browsing.NodeDescription;
import com.inductiveautomation.ignition.common.tags.model.SecurityContext;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.model.TagProvider;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.project.ProjectManager;
import com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager;

/**
 * Read/introspection services exposed to AI agents: gateway info, tag browsing and reading,
 * named query execution, and project resource listing.
 */
public class GatewayIntrospectionService {

    private static final int DEFAULT_MAX_RESULTS = 200;
    private static final int HARD_MAX_RESULTS = 1000;
    private static final long TIMEOUT_SECONDS = 10;

    private final GatewayContext context;

    public GatewayIntrospectionService(GatewayContext context) {
        this.context = context;
    }

    public Map<String, Object> info() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("state", String.valueOf(context.getState()));
        payload.put("stateMessage", context.getStateMessage());
        payload.put("javaVersion", System.getProperty("java.version"));
        Runtime runtime = Runtime.getRuntime();
        payload.put("memoryUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576);
        payload.put("memoryTotalMb", runtime.totalMemory() / 1_048_576);
        payload.put("module", AgentToolsRouteHandlers.MODULE_ID);
        payload.put("moduleVersion", AgentToolsRouteHandlers.MODULE_VERSION);

        GatewayTagManager tagManager = context.getTagManager();
        payload.put("tagProviders", tagManager == null ? List.of()
                : new ArrayList<>(tagManager.getTagProviderNames()));

        ProjectManager projectManager = context.getProjectManager();
        payload.put("projects", projectManager.getNames());
        return payload;
    }

    public List<String> tagProviders() {
        GatewayTagManager tagManager = context.getTagManager();
        return tagManager == null ? List.of() : new ArrayList<>(tagManager.getTagProviderNames());
    }

    public Map<String, Object> browseTags(String pathString, boolean recursive, Integer maxResultsIn)
            throws IllegalArgumentException {
        if (pathString == null || pathString.isBlank()) {
            throw new IllegalArgumentException(
                    "'path' is required (e.g. '[default]' or '[default]Area/Motor'). Use /tags/providers to list providers.");
        }
        TagPath path = TagPathParser.parseSafe(pathString.trim());
        if (path == null) {
            throw new IllegalArgumentException("Invalid tag path: '" + pathString + "'.");
        }
        TagProvider provider = providerFor(path.getSource());
        if (provider == null) {
            throw new IllegalArgumentException("Tag provider not found: '" + path.getSource() + "'.");
        }
        int maxResults = clamp(maxResultsIn == null ? DEFAULT_MAX_RESULTS : maxResultsIn);

        BrowseFilter filter = new BrowseFilter().setRecursive(recursive).setMaxResults(maxResults);
        try {
            Results<NodeDescription> results = provider
                    .browseAsync(path, filter, SecurityContext.emptyContext())
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            List<Map<String, Object>> nodes = new ArrayList<>();
            if (results != null && results.getResults() != null) {
                for (NodeDescription node : results.getResults()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", node.getName());
                    entry.put("path", node.getFullPath() == null ? null : node.getFullPath().toStringFull());
                    entry.put("type", node.getObjectType() == null ? null : node.getObjectType().name());
                    entry.put("hasChildren", node.hasChildren());
                    entry.put("dataType", node.getDataType() == null ? null : node.getDataType().toString());
                    QualifiedValue current = node.getCurrentValue();
                    if (current != null && current.getValue() != null && !(current.getValue() instanceof Throwable)) {
                        entry.put("currentValue", jsonSafe(current.getValue()));
                        entry.put("quality", String.valueOf(current.getQuality()));
                    }
                    nodes.add(entry);
                }
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("path", pathString.trim());
            payload.put("count", nodes.size());
            payload.put("nodes", nodes);
            return payload;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Tag browse failed: " + rootMessage(exception));
        }
    }

    public Map<String, Object> readTags(List<String> paths) throws IllegalArgumentException {
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("'paths' must be a non-empty array of tag path strings.");
        }
        List<TagPath> valid = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> invalid = new ArrayList<>();

        for (String pathString : paths) {
            if (pathString == null || pathString.isBlank()) {
                invalid.add(String.valueOf(pathString));
                continue;
            }
            TagPath parsed = TagPathParser.parseSafe(pathString.trim());
            if (parsed == null) {
                invalid.add(pathString);
            } else {
                valid.add(parsed);
            }
        }

        if (!valid.isEmpty()) {
            TagProvider provider = providerFor(valid.get(0).getSource());
            if (provider == null) {
                throw new IllegalArgumentException("Tag provider not found: '" + valid.get(0).getSource() + "'.");
            }
            try {
                List<QualifiedValue> values = provider
                        .readAsync(valid, SecurityContext.emptyContext())
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                for (int index = 0; index < valid.size(); index++) {
                    TagPath tagPath = valid.get(index);
                    QualifiedValue value = index < values.size() ? values.get(index) : QualifiedValue.NOT_FOUND;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("path", tagPath.toStringFull());
                    row.put("value", value == null ? null : jsonSafe(value.getValue()));
                    row.put("quality", value == null ? "Unknown" : String.valueOf(value.getQuality()));
                    row.put("timestamp", value == null || value.getTimestamp() == null
                            ? null : formatTimestamp(value.getTimestamp()));
                    rows.add(row);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Tag read failed: " + rootMessage(exception));
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("values", rows);
        payload.put("invalidPaths", invalid);
        return payload;
    }

    /**
     * Executes a stored named query: {@code execute(project, path, params, canCache=false,
     * canLimit=true, tx=null, getKey=false)} - fresh execution honoring configured limits.
     */
    public Map<String, Object> runNamedQuery(String project, String queryPath, JsonObject parameters)
            throws IllegalArgumentException {
        if (queryPath == null || queryPath.isBlank()) {
            throw new IllegalArgumentException("'queryPath' is required.");
        }
        String projectName = project == null || project.isBlank() ? "" : project.trim();

        NamedQueryManager manager = context.getNamedQueryManager();
        if (manager == null) {
            throw new IllegalStateException("Named Query Manager unavailable.");
        }

        Map<String, Object> params = new HashMap<>();
        if (parameters != null) {
            for (Map.Entry<String, JsonElement> entry : parameters.entrySet()) {
                params.put(entry.getKey(), primitiveValue(entry.getValue()));
            }
        }

        try {
            Object result = manager.execute(projectName, queryPath.trim(), params, false, true, null, false);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("queryPath", queryPath.trim());
            payload.put("project", projectName);
            payload.put("result", convertResult(result));
            return payload;
        } catch (Exception exception) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", false);
            payload.put("queryPath", queryPath.trim());
            payload.put("project", projectName);
            payload.put("error", rootMessage(exception));
            return payload;
        }
    }

    public Map<String, Object> listResources(String project, String containsFilter, Integer maxResultsIn)
            throws IllegalArgumentException {
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("'project' is required. Use /projects to list names.");
        }
        int maxResults = clamp(maxResultsIn == null ? DEFAULT_MAX_RESULTS : maxResultsIn);
        ProjectManager projectManager = context.getProjectManager();
        Optional<RuntimeResourceCollection> collection = projectManager.find(project.trim());
        if (!collection.isPresent()) {
            throw new IllegalArgumentException("Project not found: '" + project
                    + "'. Available: " + projectManager.getNames());
        }

        List<Map<String, Object>> items = new ArrayList<>();
        boolean truncated = false;
        for (Map.Entry<ResourceId, Resource> entry : collection.get().getAllResources().entrySet()) {
            Resource resource = entry.getValue();
            ResourcePath resourcePath = resource.getResourcePath();
            if (resource.isFolder() || resourcePath.isModuleFolder() || resourcePath.isResourceTypeFolder()) {
                continue;
            }
            String pathString = resource.getResourceType().moduleId() + "/"
                    + resource.getResourceType().typeId() + "/" + resource.getFolderPath()
                    + (resource.isUnary() ? "" : "/" + resource.getResourceName());

            if (containsFilter != null && !containsFilter.isBlank()
                    && !pathString.contains(containsFilter)) {
                continue;
            }
            if (items.size() >= maxResults) {
                truncated = true;
                break;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", pathString);
            item.put("name", resource.getResourceName());
            item.put("folder", resource.getFolderPath());
            item.put("type", resource.getResourceType().typeId());
            item.put("dataKeys", new ArrayList<>(resource.getDataKeys()));
            String documentation = resource.getDocumentation();
            item.put("documentation", documentation == null ? ""
                    : documentation.substring(0, Math.min(documentation.length(), 300)));
            items.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project", project.trim());
        payload.put("count", items.size());
        payload.put("truncated", truncated);
        payload.put("resources", items);
        return payload;
    }

    public List<String> projectNames() {
        return context.getProjectManager().getNames();
    }

    // --- helpers ---------------------------------------------------------------------------------

    private TagProvider providerFor(String source) {
        GatewayTagManager tagManager = context.getTagManager();
        if (tagManager == null || source == null) {
            return null;
        }
        return tagManager.getTagProvider(source);
    }

    private static int clamp(Integer requested) {
        int value = requested == null ? DEFAULT_MAX_RESULTS : requested;
        return Math.max(1, Math.min(value, HARD_MAX_RESULTS));
    }

    /** SimpleDateFormat is not thread-safe - create per call. */
    private static String formatTimestamp(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(date);
    }

    /** Gson-safe projection of arbitrary tag/query values. */
    private static Object jsonSafe(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof Date date) {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(date);
        }
        if (value instanceof byte[] bytes) {
            return "<" + bytes.length + " bytes>";
        }
        if (value instanceof Object[] array) {
            List<Object> converted = new ArrayList<>();
            for (Object element : array) {
                converted.add(jsonSafe(element));
            }
            return converted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> converted = new ArrayList<>();
            for (Object element : iterable) {
                converted.add(jsonSafe(element));
            }
            return converted;
        }
        return String.valueOf(value);
    }

    private static Object primitiveValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return primitive.getAsNumber();
            }
            return primitive.getAsString();
        }
        return String.valueOf(element);
    }

    /** Converts named query results (datasets, lists, scalars) into JSON-friendly structures. */
    private static Object convertResult(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof Dataset dataset) {
            List<String> columns = new ArrayList<>();
            for (int column = 0; column < dataset.getColumnCount(); column++) {
                columns.add(dataset.getColumnName(column));
            }
            List<List<Object>> rows = new ArrayList<>();
            for (int row = 0; row < dataset.getRowCount(); row++) {
                List<Object> values = new ArrayList<>();
                for (int column = 0; column < dataset.getColumnCount(); column++) {
                    values.add(jsonSafe(dataset.getValueAt(row, column)));
                }
                rows.add(values);
            }
            Map<String, Object> table = new LinkedHashMap<>();
            table.put("kind", "dataset");
            table.put("columns", columns);
            table.put("rowCount", dataset.getRowCount());
            table.put("rows", rows);
            return table;
        }
        return jsonSafe(result);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage() == null ? root.toString() : root.getMessage());
    }
}
