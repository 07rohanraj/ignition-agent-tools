package com.axcend.ignition.agenttools.diagnostic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.axcend.ignition.agenttools.validate.ComponentCatalog;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.resourcecollection.Resource;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.common.resourcecollection.RuntimeResourceCollection;
import com.inductiveautomation.ignition.common.tags.model.TagManager;
import com.inductiveautomation.ignition.common.tags.paths.TagPathValidator;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.project.ProjectManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for diagnosing Perspective views by inspecting their JSON structure,
 * component tree, bindings, and property configurations.
 *
 * <p>This service performs static analysis of view JSON documents. It does not
 * require a live Perspective session and works entirely from the project resources.</p>
 */
public class DiagnosticService {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticService.class);

    private static final String PERSPECTIVE_MODULE_ID = "com.inductiveautomation.perspective";
    private static final String VIEW_TYPE_ID = "view";
    private static final String VIEW_JSON_KEY = "view.json";

    private static final int MAX_COMPONENTS = 1000;
    private static final int MAX_BINDINGS = 500;

    private final GatewayContext gatewayContext;
    private final IgnitionExpressionValidator expressionValidator;
    private TagPathValidator tagPathValidator;

    public DiagnosticService(GatewayContext gatewayContext) {
        this.gatewayContext = gatewayContext;
        this.expressionValidator = new IgnitionExpressionValidator();
    }

    /**
     * Lazy, gateway-safe access to Ignition's native tag-path semantically-validating
     * {@link TagPathValidator}. It resolves each path against the live tag manager and
     * reports a {@link TagPathValidator.Quality} (GOOD / syntax / existence / validation
     * errors), which replaces the previous hand-rolled parse + provider read + quality
     * check.
     */
    private TagPathValidator getTagPathValidator() {
        if (tagPathValidator == null) {
            TagManager tagManager = gatewayContext.getTagManager();
            if (tagManager != null) {
                tagPathValidator = new TagPathValidator(tagManager);
            }
        }
        return tagPathValidator;
    }

    /**
     * Result of natively validating a single tag path string.
     */
    private record TagPathCheck(boolean valid, String quality, String message) {}

    /**
     * Semantic tag-path validation backed by {@link TagPathValidator}. Uses the native
     * {@code validate(List)} which returns quality and a message per path. Returns
     * {@code UNVALIDATED} when the validator is unavailable (no tag manager).
     */
    private TagPathCheck checkTagPath(String tagPath) {
        TagPathValidator validator = getTagPathValidator();
        if (validator == null) {
            return new TagPathCheck(false, "UNVALIDATED", "Unable to validate tag path: " + tagPath);
        }
        TagPathValidator.ValidatedTagPath result = validator.validate(List.of(tagPath)).get(tagPath);
        if (result == null) {
            return new TagPathCheck(false, "UNVALIDATED", "Unable to validate tag path: " + tagPath);
        }
        if (result.quality() == TagPathValidator.Quality.GOOD) {
            return new TagPathCheck(true, "Good", null);
        }
        return new TagPathCheck(false, result.quality().name(), result.message());
    }

    /**
     * Get comprehensive diagnostics for a Perspective view.
     */
    public ViewDiagnostics getViewDiagnostics(String projectName, String viewPath) {
        logger.info("Getting view diagnostics. project={} view={}", projectName, viewPath);

        ViewDiagnostics.Builder builder = ViewDiagnostics.builder(viewPath);

        try {
            // 1. Read the view JSON from project resources
            JsonObject viewJson = readViewJson(projectName, viewPath);
            if (viewJson == null) {
                builder.addError(DiagnosticIssue.error(
                        "VIEW_NOT_FOUND",
                        DiagnosticIssue.Category.STRUCTURE,
                        "View not found in project: " + viewPath,
                        "$"
                ));
                return builder.build();
            }

            // 2. Parse the view document
            JsonObject viewDocument = parseViewDocument(viewJson);
            if (viewDocument == null) {
                builder.addError(DiagnosticIssue.error(
                        "INVALID_VIEW_DOCUMENT",
                        DiagnosticIssue.Category.STRUCTURE,
                        "View JSON is not a valid document",
                        "$"
                ));
                return builder.build();
            }

            // 3. Extract the root component
            JsonObject rootComponent = extractRootComponent(viewDocument);
            if (rootComponent == null) {
                builder.addError(DiagnosticIssue.error(
                        "MISSING_ROOT_COMPONENT",
                        DiagnosticIssue.Category.STRUCTURE,
                        "View document has no root component",
                        "$"
                ));
                return builder.build();
            }

            // 4. Walk the component tree and collect diagnostics
            int[] stats = new int[]{0, 0}; // componentCount, bindingCount
            walkComponentTree(rootComponent, "$.root", builder, stats);
            builder.componentCount(stats[0]);
            builder.bindingCount(stats[1]);

        } catch (Exception e) {
            logger.error("Failed to get view diagnostics", e);
            builder.addError(DiagnosticIssue.error(
                    "DIAGNOSTIC_ERROR",
                    DiagnosticIssue.Category.STRUCTURE,
                    "Failed to analyze view: " + e.getMessage(),
                    "$"
            ));
        }

        return builder.build();
    }

    /**
     * Runs full view diagnostics and returns the findings in Ignition's native
     * {@link com.inductiveautomation.ignition.gateway.config.ValidationErrors} container (see
     * {@link ValidationErrorsMapper}). The native form is message/field-oriented and carries a
     * compact diagnostic {@code code} prefix per issue; it is provided for gateway-consumer interop
     * without altering the richer {@link DiagnosticIssue} wire contract.
     *
     * @return a native {@code ValidationErrors}; never {@code null}
     */
    public com.inductiveautomation.ignition.gateway.config.ValidationErrors getViewNativeValidationErrors(
            String projectName, String viewPath) {
        ViewDiagnostics diagnostics = getViewDiagnostics(projectName, viewPath);
        List<DiagnosticIssue> all = new ArrayList<>(diagnostics.errors());
        all.addAll(diagnostics.warnings());
        return ValidationErrorsMapper.toValidationErrors(all);
    }

    /**
     * Get diagnostics for a specific component.
     */
    public ComponentDiagnostics getComponentDiagnostics(
            String projectName, String viewPath, String componentPath) {
        logger.info("Getting component diagnostics. project={} view={} component={}",
                projectName, viewPath, componentPath);

        ComponentDiagnostics.Builder builder = ComponentDiagnostics.builder(viewPath, componentPath);

        try {
            // 1. Read the view JSON
            JsonObject viewJson = readViewJson(projectName, viewPath);
            if (viewJson == null) {
                builder.addError(DiagnosticIssue.error(
                        "VIEW_NOT_FOUND",
                        DiagnosticIssue.Category.STRUCTURE,
                        "View not found in project: " + viewPath,
                        componentPath
                ));
                return builder.build();
            }

            // 2. Parse and find the component
            JsonObject viewDocument = parseViewDocument(viewJson);
            JsonObject rootComponent = extractRootComponent(viewDocument);
            if (rootComponent == null) {
                builder.addError(DiagnosticIssue.error(
                        "MISSING_ROOT_COMPONENT",
                        DiagnosticIssue.Category.STRUCTURE,
                        "View has no root component",
                        componentPath
                ));
                return builder.build();
            }

            // 3. Navigate to the target component
            JsonObject targetComponent = navigateToComponent(rootComponent, componentPath);
            if (targetComponent == null) {
                builder.addError(DiagnosticIssue.error(
                        "COMPONENT_NOT_FOUND",
                        DiagnosticIssue.Category.COMPONENT,
                        "Component not found at path: " + componentPath,
                        componentPath
                ));
                return builder.build();
            }

            // 4. Extract component info
            String componentType = getStringValue(targetComponent, "type");
            String componentName = getComponentName(targetComponent);
            builder.componentType(componentType);
            builder.componentName(componentName);

            // 5. Validate the component
            validateComponent(targetComponent, componentPath, builder);

        } catch (Exception e) {
            logger.error("Failed to get component diagnostics", e);
            builder.addError(DiagnosticIssue.error(
                    "DIAGNOSTIC_ERROR",
                    DiagnosticIssue.Category.STRUCTURE,
                    "Failed to analyze component: " + e.getMessage(),
                    componentPath
            ));
        }

        return builder.build();
    }

    /**
     * Get diagnostics for a specific binding.
     */
    public BindingDiagnostics getBindingDiagnostics(
            String projectName, String viewPath,
            String componentPath, String propertyPath) {
        logger.info("Getting binding diagnostics. project={} view={} component={} property={}",
                projectName, viewPath, componentPath, propertyPath);

        BindingDiagnostics.Builder builder = BindingDiagnostics.builder(viewPath, componentPath, propertyPath);

        try {
            // 1. Read the view JSON
            JsonObject viewJson = readViewJson(projectName, viewPath);
            if (viewJson == null) {
                builder.addError(DiagnosticIssue.error(
                        "VIEW_NOT_FOUND",
                        DiagnosticIssue.Category.STRUCTURE,
                        "View not found in project: " + viewPath,
                        componentPath + "." + propertyPath
                ));
                return builder.build();
            }

            // 2. Parse and find the component
            JsonObject viewDocument = parseViewDocument(viewJson);
            JsonObject rootComponent = extractRootComponent(viewDocument);
            JsonObject targetComponent = navigateToComponent(rootComponent, componentPath);
            if (targetComponent == null) {
                builder.addError(DiagnosticIssue.error(
                        "COMPONENT_NOT_FOUND",
                        DiagnosticIssue.Category.COMPONENT,
                        "Component not found at path: " + componentPath,
                        componentPath + "." + propertyPath
                ));
                return builder.build();
            }

            // 3. Find the binding configuration
            JsonObject bindingConfig = findBindingConfig(targetComponent, propertyPath, viewDocument);
            if (bindingConfig == null) {
                builder.quality("No Binding");
                builder.message("No binding found for property: " + propertyPath);
                return builder.build();
            }

            // 4. Validate the binding
            validateBinding(bindingConfig, componentPath, propertyPath, builder);

        } catch (Exception e) {
            logger.error("Failed to get binding diagnostics", e);
            builder.addError(DiagnosticIssue.error(
                    "DIAGNOSTIC_ERROR",
                    DiagnosticIssue.Category.BINDING,
                    "Failed to analyze binding: " + e.getMessage(),
                    componentPath + "." + propertyPath
            ));
        }

        return builder.build();
    }

    // --- Helper methods ---

    private JsonObject readViewJson(String projectName, String viewPath) {
        ProjectManager projectManager = gatewayContext.getProjectManager();
        Optional<RuntimeResourceCollection> collection = projectManager.find(projectName);
        if (collection.isEmpty()) {
            return null;
        }

        ResourcePath resourcePath = new ResourcePath(
                new ResourceType(PERSPECTIVE_MODULE_ID, VIEW_TYPE_ID),
                viewPath
        );

        Optional<Resource> resource = projectManager.getResource(projectName, resourcePath);
        if (resource.isEmpty()) {
            return null;
        }

        Optional<com.inductiveautomation.ignition.common.ImmutableBytes> data =
                resource.get().getData(VIEW_JSON_KEY);
        if (data.isEmpty()) {
            return null;
        }

        try {
            String jsonString = data.get().getBytesAsString();
            return com.inductiveautomation.ignition.common.gson.JsonParser
                    .parseString(jsonString).getAsJsonObject();
        } catch (Exception e) {
            logger.error("Failed to parse view JSON for {}/{}", projectName, viewPath, e);
            return null;
        }
    }

    private JsonObject parseViewDocument(JsonObject viewJson) {
        // Handle both full view documents and bare component trees
        if (viewJson.has("root") && viewJson.get("root").isJsonObject()) {
            return viewJson;
        }
        // Treat as bare component tree
        return viewJson;
    }

    private JsonObject extractRootComponent(JsonObject viewDocument) {
        if (viewDocument.has("root") && viewDocument.get("root").isJsonObject()) {
            return viewDocument.getAsJsonObject("root");
        }
        // Bare component tree - the document itself is the root
        if (viewDocument.has("type")) {
            return viewDocument;
        }
        return null;
    }

    private void walkComponentTree(JsonObject component, String path,
                                   DiagnosticCollector collector, int[] stats) {
        if (stats[0] >= MAX_COMPONENTS) {
            return;
        }

        stats[0]++;

        // Validate this component
        validateComponent(component, path, collector);

        // Process children
        if (component.has("children") && component.get("children").isJsonArray()) {
            JsonArray children = component.getAsJsonArray("children");
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i).isJsonObject()) {
                    JsonObject child = children.get(i).getAsJsonObject();
                    String childName = getComponentName(child);
                    String childPath = path + ".children[" + i + "]";
                    if (childName != null && !childName.isEmpty()) {
                        childPath = path + "." + childName;
                    }
                    walkComponentTree(child, childPath, collector, stats);
                }
            }
        }
    }

    private void validateComponent(JsonObject component, String path,
                                   DiagnosticCollector collector) {
        // Check component type
        String type = getStringValue(component, "type");
        if (type == null || type.isEmpty()) {
            collector.addError(DiagnosticIssue.error(
                    "MISSING_COMPONENT_TYPE",
                    DiagnosticIssue.Category.COMPONENT,
                    "Component has no 'type' field",
                    path
            ));
        } else if (!ComponentCatalog.isKnown(type)) {
            if (ComponentCatalog.canonicalFor(type).isPresent()) {
                collector.addWarning(DiagnosticIssue.warning(
                        "DEPRECATED_TYPE_ALIAS",
                        DiagnosticIssue.Category.COMPONENT,
                        "Component type '" + type + "' is a deprecated alias. Use: " +
                                ComponentCatalog.canonicalFor(type).orElse("unknown"),
                        path
                ));
            } else {
                collector.addWarning(DiagnosticIssue.warning(
                        "UNKNOWN_COMPONENT_TYPE",
                        DiagnosticIssue.Category.COMPONENT,
                        "Component type '" + type + "' is not in the standard catalog",
                        path
                ));
            }
        }

        // Check meta.name
        JsonObject meta = getJsonObject(component, "meta");
        if (meta == null) {
            collector.addWarning(DiagnosticIssue.warning(
                    "MISSING_META",
                    DiagnosticIssue.Category.COMPONENT,
                    "Component is missing 'meta' object",
                    path
            ));
        } else {
            String name = getStringValue(meta, "name");
            if (name == null || name.isEmpty()) {
                collector.addWarning(DiagnosticIssue.warning(
                        "MISSING_COMPONENT_NAME",
                        DiagnosticIssue.Category.COMPONENT,
                        "Component 'meta.name' is missing or empty",
                        path
                ));
            }
        }

        // Check props
        JsonObject props = getJsonObject(component, "props");
        if (props == null) {
            collector.addWarning(DiagnosticIssue.warning(
                    "MISSING_PROPS",
                    DiagnosticIssue.Category.COMPONENT,
                    "Component is missing 'props' object",
                    path
            ));
        }

        // Check for bindings in props
        validateComponentBindings(component, path, collector);
    }

private void validateComponentBindings(JsonObject component, String path,
                                            DiagnosticCollector collector) {
        JsonObject props = getJsonObject(component, "props");
        if (props == null) {
            return;
        }

        // Check for bindings in props (both inline shorthand and proper binding format)
        for (String key : props.keySet()) {
            JsonElement value = props.get(key);
            if (value != null && value.isJsonObject()) {
                JsonObject obj = value.getAsJsonObject();

                // Check for inline tag bindings (shorthand format: {tagPath: "..."})
                if (obj.has("tagPath") && obj.get("tagPath").isJsonPrimitive()) {
                    String tagPath = obj.get("tagPath").getAsString();
                    if (tagPath == null || tagPath.isEmpty()) {
                        collector.addError(DiagnosticIssue.error(
                                "EMPTY_TAG_PATH",
                                DiagnosticIssue.Category.BINDING,
                                "Inline tag binding has empty 'tagPath'",
                                path + ".props." + key
                        ));
                    } else {
                        // Validate inline tag binding using the same logic as proper binding config
                        JsonObject tempConfig = new JsonObject();
                        tempConfig.addProperty("tagPath", tagPath);
                        validateTagBindingConfig(tempConfig, path + ".props." + key, collector);
                    }
                }

                // Check for proper binding format (with "binding" or "type" key)
                if (obj.has("binding") || obj.has("type")) {
                    validateProperBindingFormat(obj, path + ".props." + key, collector);
                }
            }
        }
    }

    private void validateProperBindingFormat(JsonObject bindingObj, String bindingPath, DiagnosticCollector collector) {
        // Check for binding.type or binding.config
        if (bindingObj.has("binding") && bindingObj.get("binding").isJsonObject()) {
            JsonObject binding = bindingObj.getAsJsonObject("binding");
            validateBindingConfig(binding, bindingPath + ".binding", collector);
        } else if (bindingObj.has("type")) {
            // Direct binding format: {type: "tag", config: {...}}
            validateBindingConfig(bindingObj, bindingPath, collector);
        }
    }

    private void validateBindingConfig(JsonObject binding, String bindingPath, DiagnosticCollector collector) {
        String bindingType = getStringValue(binding, "type");
        if (bindingType == null || bindingType.isEmpty()) {
            collector.addError(DiagnosticIssue.error(
                    "MISSING_BINDING_TYPE",
                    DiagnosticIssue.Category.BINDING,
                    "Binding has no 'type' field",
                    bindingPath
            ));
            return;
        }

        // Validate binding-specific configuration
        JsonObject config = getJsonObject(binding, "config");
        if (config == null) {
            collector.addError(DiagnosticIssue.error(
                    "MISSING_BINDING_CONFIG",
                    DiagnosticIssue.Category.BINDING,
                    "Binding has no 'config' object",
                    bindingPath
            ));
            return;
        }

        // Delegate to specific binding validators
        switch (bindingType) {
            case "tag" -> validateTagBindingConfig(config, bindingPath, collector);
            case "query" -> validateQueryBindingConfig(config, bindingPath, collector);
            case "expression" -> validateExpressionBindingConfig(config, bindingPath, collector);
            case "property" -> validatePropertyBindingConfig(config, bindingPath, collector);
            case "tag-history", "http", "message", "udtParameter" -> {
                // These are valid but we don't have specific validation yet
            }
            default -> collector.addWarning(DiagnosticIssue.warning(
                    "UNKNOWN_BINDING_TYPE",
                    DiagnosticIssue.Category.BINDING,
                    "Unknown binding type: " + bindingType,
                    bindingPath
            ));
        }
    }

    private void validateTagBindingConfig(JsonObject config, String bindingPath, DiagnosticCollector collector) {
        String tagPath = getStringValue(config, "tagPath");
        if (tagPath == null || tagPath.isEmpty()) {
            collector.addError(DiagnosticIssue.error(
                    "MISSING_TAG_PATH",
                    DiagnosticIssue.Category.BINDING,
                    "Tag binding config has no 'tagPath'",
                    bindingPath
            ));
            return;
        }

        // Semantic tag-path validation via Ignition's native TagPathValidator
        TagPathCheck check = checkTagPath(tagPath);
        if (!check.valid()) {
            collector.addWarning(DiagnosticIssue.warning(
                    "TAG_PATH_NOT_VALID",
                    DiagnosticIssue.Category.BINDING,
                    "Tag path not valid (" + check.quality() + "): " + tagPath
                            + (check.message() != null ? " - " + check.message() : ""),
                    bindingPath
            ));
        }
    }

    private void validateQueryBindingConfig(JsonObject config, String bindingPath, DiagnosticCollector collector) {
        String queryPath = getStringValue(config, "queryPath");
        if (queryPath == null || queryPath.isEmpty()) {
            collector.addError(DiagnosticIssue.error(
                    "MISSING_QUERY_PATH",
                    DiagnosticIssue.Category.BINDING,
                    "Query binding config has no 'queryPath'",
                    bindingPath
            ));
            return;
        }

        // Validate parameters if present
        if (config.has("parameters")) {
            JsonElement params = config.get("parameters");
            if (!params.isJsonObject()) {
                collector.addError(DiagnosticIssue.error(
                        "INVALID_QUERY_PARAMETERS",
                        DiagnosticIssue.Category.BINDING,
                        "Query binding parameters must be an object",
                        bindingPath
                ));
            }
        }

        // Validate pollRate if present
        if (config.has("pollRate")) {
            JsonElement pollRate = config.get("pollRate");
            if (!pollRate.isJsonPrimitive() || !pollRate.getAsJsonPrimitive().isNumber()) {
                collector.addError(DiagnosticIssue.error(
                        "INVALID_POLL_RATE",
                        DiagnosticIssue.Category.BINDING,
                        "Query binding pollRate must be a number",
                        bindingPath
                ));
            }
        }

        // Check if named query exists
        if (!checkNamedQueryExists(queryPath)) {
            collector.addError(DiagnosticIssue.error(
                    "QUERY_NOT_FOUND",
                    DiagnosticIssue.Category.BINDING,
                    "Named query not found: " + queryPath,
                    bindingPath
            ));
        }
    }

    private void validateExpressionBindingConfig(JsonObject config, String bindingPath, DiagnosticCollector collector) {
        String expression = getStringValue(config, "expression");
        if (expression == null || expression.isEmpty()) {
            collector.addError(DiagnosticIssue.error(
                    "MISSING_EXPRESSION",
                    DiagnosticIssue.Category.BINDING,
                    "Expression binding config has no 'expression'",
                    bindingPath
            ));
            return;
        }

        // Use Ignition's actual expression validation infrastructure (single source of truth).
        IgnitionExpressionValidator.ValidationResult result = expressionValidator.validateExpression(
                expression, bindingPath, bindingPath);
        emitExpressionIssues(result, bindingPath, collector);
    }

    private void emitExpressionIssues(IgnitionExpressionValidator.ValidationResult result,
                                      String bindingPath, DiagnosticCollector collector) {
        for (IgnitionExpressionValidator.Issue issue : result.issues()) {
            if (issue.severity() == IgnitionExpressionValidator.Severity.ERROR) {
                collector.addError(DiagnosticIssue.error(
                        issue.code(),
                        DiagnosticIssue.Category.BINDING,
                        issue.message(),
                        bindingPath
                ));
            } else {
                collector.addWarning(DiagnosticIssue.warning(
                        issue.code(),
                        DiagnosticIssue.Category.BINDING,
                        issue.message(),
                        bindingPath
                ));
            }
        }
    }

    private void validatePropertyBindingConfig(JsonObject config, String bindingPath, DiagnosticCollector collector) {
        String property = getStringValue(config, "property");
        if (property == null || property.isEmpty()) {
            collector.addError(DiagnosticIssue.error(
                    "MISSING_PROPERTY_PATH",
                    DiagnosticIssue.Category.BINDING,
                    "Property binding config has no 'property' path",
                    bindingPath
            ));
            return;
        }

        if (!isValidPropertyPath(property)) {
            collector.addError(DiagnosticIssue.error(
                    "INVALID_PROPERTY_PATH",
                    DiagnosticIssue.Category.BINDING,
                    "Invalid property path format: " + property,
                    bindingPath
            ));
        }
    }

    private void validateBinding(JsonObject bindingConfig, String componentPath,
                                 String propertyPath, BindingDiagnostics.Builder builder) {
        String bindingType = getStringValue(bindingConfig, "type");
        builder.bindingType(bindingType);

        if (bindingType == null || bindingType.isEmpty()) {
            builder.addError(DiagnosticIssue.error(
                    "MISSING_BINDING_TYPE",
                    DiagnosticIssue.Category.BINDING,
                    "Binding has no 'type' field",
                    componentPath + "." + propertyPath
            ));
            return;
        }

        // Validate binding-specific configuration
        JsonObject config = getJsonObject(bindingConfig, "config");
        if (config == null) {
            builder.addError(DiagnosticIssue.error(
                    "MISSING_BINDING_CONFIG",
                    DiagnosticIssue.Category.BINDING,
                    "Binding has no 'config' object",
                    componentPath + "." + propertyPath
            ));
            return;
        }

        switch (bindingType) {
            case "tag" -> validateTagBinding(config, componentPath, propertyPath, builder);
            case "query" -> validateQueryBinding(config, componentPath, propertyPath, builder);
            case "expression" -> validateExpressionBinding(config, componentPath, propertyPath, builder);
            case "property" -> validatePropertyBinding(config, componentPath, propertyPath, builder);
            default -> {
                // Unknown binding type - just warn
                builder.addError(DiagnosticIssue.error(
                        "UNKNOWN_BINDING_TYPE",
                        DiagnosticIssue.Category.BINDING,
                        "Unknown binding type: " + bindingType,
                        componentPath + "." + propertyPath
                ));
            }
        }
    }

private void validateTagBinding(JsonObject config, String componentPath,
                                     String propertyPath, BindingDiagnostics.Builder builder) {
        String tagPath = getStringValue(config, "tagPath");
        if (tagPath == null || tagPath.isEmpty()) {
            builder.addError(DiagnosticIssue.error(
                    "MISSING_TAG_PATH",
                    DiagnosticIssue.Category.BINDING,
                    "Tag binding has no 'tagPath'",
                    componentPath + "." + propertyPath
            ));
            return;
        }

        // Semantic tag-path validation via Ignition's native TagPathValidator
        TagPathCheck check = checkTagPath(tagPath);
        if (check.valid()) {
            builder.quality("Good");
        } else {
            builder.quality(check.quality());
            builder.addWarning(DiagnosticIssue.warning(
                    "TAG_PATH_NOT_VALID",
                    DiagnosticIssue.Category.BINDING,
                    "Tag path not valid (" + check.quality() + "): " + tagPath
                            + (check.message() != null ? " - " + check.message() : ""),
                    componentPath + "." + propertyPath
            ));
        }
    }

private void validateQueryBinding(JsonObject config, String componentPath,
                                       String propertyPath, BindingDiagnostics.Builder builder) {
        String queryPath = getStringValue(config, "queryPath");
        if (queryPath == null || queryPath.isEmpty()) {
            builder.addError(DiagnosticIssue.error(
                    "MISSING_QUERY_PATH",
                    DiagnosticIssue.Category.BINDING,
                    "Query binding has no 'queryPath'",
                    componentPath + "." + propertyPath
            ));
            return;
        }

        // Validate query parameters if present
        if (config.has("parameters")) {
            JsonElement params = config.get("parameters");
            if (!params.isJsonObject()) {
                builder.addError(DiagnosticIssue.error(
                        "INVALID_QUERY_PARAMETERS",
                        DiagnosticIssue.Category.BINDING,
                        "Query binding parameters must be an object",
                        componentPath + "." + propertyPath
                ));
            }
        }

        // Validate pollRate if present
        if (config.has("pollRate")) {
            JsonElement pollRate = config.get("pollRate");
            if (!pollRate.isJsonPrimitive() || !pollRate.getAsJsonPrimitive().isNumber()) {
                builder.addError(DiagnosticIssue.error(
                        "INVALID_POLL_RATE",
                        DiagnosticIssue.Category.BINDING,
                        "Query binding pollRate must be a number",
                        componentPath + "." + propertyPath
                ));
            } else {
                int rate = pollRate.getAsInt();
                if (rate < 0) {
                    builder.addWarning(DiagnosticIssue.warning(
                            "NEGATIVE_POLL_RATE",
                            DiagnosticIssue.Category.BINDING,
                            "Query binding pollRate should not be negative",
                            componentPath + "." + propertyPath
                    ));
                }
            }
        }

        // Check if named query exists
        if (queryPath != null && !queryPath.isEmpty()) {
            boolean exists = checkNamedQueryExists(queryPath);
            if (!exists) {
                builder.addError(DiagnosticIssue.error(
                        "QUERY_NOT_FOUND",
                        DiagnosticIssue.Category.BINDING,
                        "Named query not found: " + queryPath,
                        componentPath + "." + propertyPath
                ));
            } else {
                builder.quality("Good");
            }
        }
    }

private void validateExpressionBinding(JsonObject config, String componentPath,
                                             String propertyPath, BindingDiagnostics.Builder builder) {
        String expression = getStringValue(config, "expression");
        if (expression == null || expression.isEmpty()) {
            builder.addError(DiagnosticIssue.error(
                    "MISSING_EXPRESSION",
                    DiagnosticIssue.Category.BINDING,
                    "Expression binding has no 'expression'",
                    componentPath + "." + propertyPath
            ));
            return;
        }

        // Use Ignition's actual expression validation infrastructure (single source of truth).
        String bindingPath = componentPath + "." + propertyPath;
        IgnitionExpressionValidator.ValidationResult result = expressionValidator.validateExpression(
                expression, componentPath, propertyPath);

        boolean anyError = false;
        for (IgnitionExpressionValidator.Issue issue : result.issues()) {
            if (issue.severity() == IgnitionExpressionValidator.Severity.ERROR) {
                anyError = true;
                builder.addError(DiagnosticIssue.error(
                        issue.code(),
                        DiagnosticIssue.Category.BINDING,
                        issue.message(),
                        bindingPath
                ));
            } else {
                builder.addWarning(DiagnosticIssue.warning(
                        issue.code(),
                        DiagnosticIssue.Category.BINDING,
                        issue.message(),
                        bindingPath
                ));
            }
        }
        builder.quality(anyError ? "Bad" : "Good");
    }

private void validatePropertyBinding(JsonObject config, String componentPath,
                                          String propertyPath, BindingDiagnostics.Builder builder) {
        String property = getStringValue(config, "property");
        if (property == null || property.isEmpty()) {
            builder.addError(DiagnosticIssue.error(
                    "MISSING_PROPERTY_PATH",
                    DiagnosticIssue.Category.BINDING,
                    "Property binding has no 'property' path",
                    componentPath + "." + propertyPath
            ));
            return;
        }

        // Validate property path format
        if (!isValidPropertyPath(property)) {
            builder.addError(DiagnosticIssue.error(
                    "INVALID_PROPERTY_PATH",
                    DiagnosticIssue.Category.BINDING,
                    "Invalid property path format: " + property,
                    componentPath + "." + propertyPath
            ));
        }

        builder.quality("Good");
    }

    private boolean isValidPropertyPath(String path) {
        // Property paths should be like: view.params.name, view.custom.name, root.props.value, etc.
        // Basic validation: should contain at least one dot and not start/end with dot
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.startsWith(".") || path.endsWith(".")) {
            return false;
        }
        if (path.contains("..")) {
            return false;
        }
        // Should have at least one segment separator
        return path.contains(".");
    }

    private boolean checkNamedQueryExists(String queryPath) {
        try {
            var manager = gatewayContext.getNamedQueryManager();
            if (manager == null) {
                return false;
            }
            // Try to get the query info - this will throw if it doesn't exist
            manager.execute("", queryPath, null, false, false, null, true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JsonObject findBindingConfig(JsonObject component, String propertyPath,
                                         JsonObject viewDocument) {
        // First check propConfig in the view document
        if (viewDocument.has("propConfig") && viewDocument.get("propConfig").isJsonObject()) {
            JsonObject propConfig = viewDocument.getAsJsonObject("propConfig");
            String componentPath = getStringValue(component, "path");
            if (componentPath != null && propConfig.has(componentPath)) {
                JsonObject componentBindings = propConfig.getAsJsonObject(componentPath);
                if (componentBindings.has(propertyPath)) {
                    return componentBindings.getAsJsonObject(propertyPath);
                }
            }
        }

        // Check for inline bindings in the component's props
        JsonObject props = getJsonObject(component, "props");
        if (props != null && props.has(propertyPath)) {
            JsonElement value = props.get(propertyPath);
            if (value.isJsonObject()) {
                JsonObject obj = value.getAsJsonObject();
                if (obj.has("binding") || obj.has("type")) {
                    return obj;
                }
            }
        }

        return null;
    }

    private JsonObject navigateToComponent(JsonObject root, String componentPath) {
        if (componentPath == null || componentPath.isEmpty() || componentPath.equals("$.root") ||
                componentPath.equals("root")) {
            return root;
        }

        // Parse the path and navigate
        String[] parts = componentPath.split("\\.");
        JsonObject current = root;

        for (String part : parts) {
            if (part.equals("root") || part.equals("$")) {
                continue;
            }

            if (part.startsWith("children[")) {
                // Array index
                int index = Integer.parseInt(part.replaceAll("children\\[|\\]", ""));
                if (current.has("children") && current.get("children").isJsonArray()) {
                    JsonArray children = current.getAsJsonArray("children");
                    if (index < children.size() && children.get(index).isJsonObject()) {
                        current = children.get(index).getAsJsonObject();
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                // Named component
                if (current.has("children") && current.get("children").isJsonArray()) {
                    JsonArray children = current.getAsJsonArray("children");
                    boolean found = false;
                    for (int i = 0; i < children.size(); i++) {
                        if (children.get(i).isJsonObject()) {
                            JsonObject child = children.get(i).getAsJsonObject();
                            String name = getComponentName(child);
                            if (part.equals(name)) {
                                current = child;
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }

        return current;
    }

    private String getComponentName(JsonObject component) {
        JsonObject meta = getJsonObject(component, "meta");
        if (meta != null) {
            return getStringValue(meta, "name");
        }
        return null;
    }

    private String getStringValue(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private JsonObject getJsonObject(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonObject()) {
            return obj.getAsJsonObject(key);
        }
        return null;
    }
}
