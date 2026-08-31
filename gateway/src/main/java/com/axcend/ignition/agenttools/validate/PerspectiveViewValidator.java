package com.axcend.ignition.agenttools.validate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonPrimitive;
import com.inductiveautomation.ignition.common.expressions.parsing.ELParserHarness;
import com.inductiveautomation.ignition.common.expressions.parsing.ELParserHarness.ParseContext;
import com.inductiveautomation.ignition.common.jsonschema.ValidationMessage;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.axcend.ignition.agenttools.diagnostic.IgnitionExpressionValidator;

/**
 * Structural validator for AI-generated Perspective view JSON documents. Pure logic over parsed
 * Gson trees - no gateway services required, fully unit-testable.
 *
 * <p>Checks: required root keys (meta/props/type), recursive children shape, component type
 * sanity against {@link ComponentCatalog} (unknown = warning), flex layout basics, binding shape,
 * propConfig/events shape, duplicate sibling names, and deprecated aliases. When the component's
 * {@code props} schema is available in the classpath Perspective descriptor catalog, each
 * component's {@code props} object is additionally validated against the native
 * {@link com.inductiveautomation.ignition.common.jsonschema.JsonSchema} (see
 * {@link PerspectiveComponentSchemaCatalog}) - catching wrong types, bad enum values, unknown props
 * (where the schema forbids {@code additionalProperties}), and missing required props. Never
 * throws on malformed content; every problem is reported as an issue with a JSON-path-style
 * location.</p>
 */
public final class PerspectiveViewValidator {

    private static final int MAX_ISSUES = 200;

    private final PerspectiveComponentSchemaCatalog schemaCatalog =
            PerspectiveComponentSchemaCatalog.getInstance();

    private final IgnitionExpressionValidator expressionValidator =
            new IgnitionExpressionValidator();

    public ValidationResult validate(JsonElement root) {
        List<ValidationIssue> issues = new ArrayList<>();
        Stats stats = new Stats();

        if (root == null || root.isJsonNull()) {
            issues.add(new ValidationIssue("$", "EMPTY_DOCUMENT", ValidationIssue.Severity.ERROR,
                    "View document is null or empty."));
            return ValidationResult.of(issues);
        }
        if (!root.isJsonObject()) {
            issues.add(new ValidationIssue("$", "ROOT_NOT_OBJECT", ValidationIssue.Severity.ERROR,
                    "View document root must be a JSON object."));
            return ValidationResult.of(issues);
        }

        JsonObject rootObject = root.getAsJsonObject();
        if (rootObject.has("root") && rootObject.get("root").isJsonObject()) {
            // Full view resource: {params, custom, events, propConfig, root: {component tree}}
            checkViewWrapperKeys(rootObject, issues);
            walk(rootObject.getAsJsonObject("root"), "$.root", 0, issues, stats);
        } else {
            // Bare component tree (the root component itself).
            checkRootKeys(rootObject, issues);
            walk(rootObject, "$", 0, issues, stats);
        }

        return ValidationResult.of(issues, stats.componentCount, stats.maxDepth, stats.bindingCount);
    }

    private void checkViewWrapperKeys(JsonObject doc, List<ValidationIssue> issues) {
        for (String key : List.of("params", "custom", "propConfig", "events")) {
            JsonElement value = doc.get(key);
            if (value != null && !value.isJsonNull() && !value.isJsonObject()) {
                issues.add(new ValidationIssue("$." + key, "VIEW_KEY_NOT_OBJECT",
                        ValidationIssue.Severity.ERROR,
                        "'" + key + "' must be a JSON object when present."));
            }
        }
        JsonElement rootComponent = doc.get("root");
        if (!rootComponent.getAsJsonObject().has("type")) {
            issues.add(new ValidationIssue("$.root.type", "MISSING_ROOT_TYPE",
                    ValidationIssue.Severity.ERROR,
                    "View root component 'type' is missing or blank."));
        }
    }

    // --- structural checks -----------------------------------------------------------------------

    private void checkRootKeys(JsonObject root, List<ValidationIssue> issues) {
        requireObject(root, "meta", "$", issues);
        requireObject(root, "props", "$", issues);
        if (!root.has("type") || !root.get("type").isJsonPrimitive()
                || root.get("type").getAsString().isBlank()) {
            issues.add(new ValidationIssue("$.type", "MISSING_ROOT_TYPE", ValidationIssue.Severity.ERROR,
                    "Root component 'type' is missing or blank."));
        }
    }

    private void requireObject(JsonObject parent, String key, String path, List<ValidationIssue> issues) {
        if (!parent.has(key)) {
            issues.add(new ValidationIssue(path + "." + key, "MISSING_REQUIRED_KEY",
                    ValidationIssue.Severity.ERROR, "'" + key + "' is required."));
        } else if (!parent.get(key).isJsonObject()) {
            issues.add(new ValidationIssue(path + "." + key, "NOT_AN_OBJECT",
                    ValidationIssue.Severity.ERROR, "'" + key + "' must be a JSON object."));
        }
    }

    private void walk(JsonObject node, String path, int depth, List<ValidationIssue> issues, Stats stats) {
        if (issues.size() >= MAX_ISSUES) {
            return;
        }
        stats.maxDepth = Math.max(stats.maxDepth, depth);

        boolean hasType = node.has("type") && node.get("type").isJsonPrimitive()
                && !node.get("type").getAsString().isBlank();

        if (!hasType) {
            issues.add(new ValidationIssue(path + ".type", "MISSING_COMPONENT_TYPE",
                    ValidationIssue.Severity.ERROR, "Component 'type' is missing or blank."));
        } else {
            stats.componentCount++;
            String type = node.get("type").getAsString();
            checkComponentType(node, type, path, issues);
            checkComponentSchema(node, type, path, issues);
        }

        checkMeta(node, path, issues);
        checkChildren(node, path, depth, issues, stats);
        checkFlexLayout(node, path, issues);
        checkStylePlacement(node, path, issues);
        checkBindings(node, path, issues, stats);
        checkPropConfigAndEvents(node, path, issues);

        if (issues.size() >= MAX_ISSUES) {
            issues.add(new ValidationIssue("$", "TOO_MANY_ISSUES", ValidationIssue.Severity.WARNING,
                    "Validation stopped after " + MAX_ISSUES + " issues."));
        }
    }

    private void checkComponentType(JsonObject node, String type, String path, List<ValidationIssue> issues) {
        String canonical = ComponentCatalog.ALIASES.get(type);
        if (canonical != null) {
            issues.add(new ValidationIssue(path + ".type", "DEPRECATED_ALIAS",
                    ValidationIssue.Severity.WARNING,
                    "'" + type + "' is not a current component ID; use '" + canonical + "'."));
        } else if (type.startsWith("ia.") && !schemaCatalog.find(type).isPresent()
                && !ComponentCatalog.isKnown(type)) {
            issues.add(new ValidationIssue(path + ".type", "UNKNOWN_COMPONENT_TYPE",
                    ValidationIssue.Severity.WARNING,
                    "'" + type + "' starts with 'ia.' but is not in the known component catalog."));
        } else if (!type.contains(".")) {
            issues.add(new ValidationIssue(path + ".type", "SUSPICIOUS_TYPE_FORMAT",
                    ValidationIssue.Severity.WARNING,
                    "'" + type + "' does not look like a component ID (expected e.g. 'ia.display.label')."));
        }
    }

    private void checkMeta(JsonObject node, String path, List<ValidationIssue> issues) {
        if (!node.has("meta")) {
            issues.add(new ValidationIssue(path + ".meta", "MISSING_META",
                    ValidationIssue.Severity.ERROR, "Component is missing its 'meta' object."));
            return;
        }
        if (!node.get("meta").isJsonObject()) {
            issues.add(new ValidationIssue(path + ".meta", "META_NOT_OBJECT",
                    ValidationIssue.Severity.ERROR, "'meta' must be a JSON object."));
            return;
        }
        JsonObject meta = node.getAsJsonObject("meta");
        if (!meta.has("name") || !meta.get("name").isJsonPrimitive()
                || meta.get("name").getAsString().isBlank()) {
            issues.add(new ValidationIssue(path + ".meta.name", "MISSING_COMPONENT_NAME",
                    ValidationIssue.Severity.ERROR, "meta.name is missing or blank."));
            return;
        }
        // Native meta schema validation
        Optional<PerspectiveComponentSchemaCatalog.NativeSchema> ms =
                schemaCatalog.findMetaSchema();
        if (ms.isPresent()) {
            String name = meta.get("name").getAsString();
            // The meta schema validates name against pattern ^[a-zA-Z_][a-zA-Z_ ()\\-0-9]*$
            // and checks visible/domId/tabIndex types. We validate the name pattern; other
            // field checks are advisory (the schema has broken $ref for tooltip.style when loaded
            // standalone, so we skip those).
            if (!name.matches("^[a-zA-Z_][a-zA-Z_ ()\\-0-9]*$")) {
                issues.add(new ValidationIssue(path + ".meta.name", "SCHEMA_PATTERN",
                        ValidationIssue.Severity.ERROR,
                        "Component name '" + name + "' contains invalid characters. Names may only " +
                        "contain letters, digits, spaces, hyphens, parentheses, and underscores."));
            }
        }
    }

    private void checkChildren(JsonObject node, String path, int depth,
                               List<ValidationIssue> issues, Stats stats) {
        if (!node.has("children")) {
            return;
        }
        JsonElement children = node.get("children");
        if (!children.isJsonArray()) {
            issues.add(new ValidationIssue(path + ".children", "CHILDREN_NOT_ARRAY",
                    ValidationIssue.Severity.ERROR, "'children' must be an array."));
            return;
        }
        JsonArray array = children.getAsJsonArray();
        detectDuplicateSiblingNames(array, path, issues);
        int index = 0;
        for (JsonElement child : array) {
            String childPath = path + ".children[" + index++ + "]";
            if (!child.isJsonObject()) {
                issues.add(new ValidationIssue(childPath, "CHILD_NOT_OBJECT",
                        ValidationIssue.Severity.ERROR, "Every child must be a JSON object."));
                continue;
            }
            walk(child.getAsJsonObject(), childPath, depth + 1, issues, stats);
        }
    }

    private void detectDuplicateSiblingNames(JsonArray children, String path, List<ValidationIssue> issues) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> reported = new LinkedHashSet<>();
        int index = 0;
        for (JsonElement child : children) {
            String childPath = path + ".children[" + index++ + "]";
            if (!child.isJsonObject()) {
                continue;
            }
            JsonObject childObject = child.getAsJsonObject();
            if (!childObject.has("meta") || !childObject.getAsJsonObject("meta").has("name")) {
                continue;
            }
            JsonElement nameElement = childObject.getAsJsonObject("meta").get("name");
            if (!nameElement.isJsonPrimitive()) {
                continue;
            }
            String name = nameElement.getAsString();
            if (!name.isBlank() && !seen.add(name) && reported.add(name)) {
                issues.add(new ValidationIssue(childPath, "DUPLICATE_SIBLING_NAME",
                        ValidationIssue.Severity.WARNING,
                        "Duplicate sibling component name '" + name + "'."));
            }
        }
    }

    private void checkFlexLayout(JsonObject node, String path, List<ValidationIssue> issues) {
        if (!node.has("layout")) {
            return;
        }
        if (!node.get("layout").isJsonObject()) {
            issues.add(new ValidationIssue(path + ".layout", "LAYOUT_NOT_OBJECT",
                    ValidationIssue.Severity.ERROR, "'layout' must be a JSON object when present."));
            return;
        }
        JsonObject layout = node.getAsJsonObject("layout");
        for (String key : List.of("grow", "shrink")) {
            if (layout.has(key)) {
                JsonElement value = layout.get(key);
                boolean numeric = value.isJsonPrimitive() && ((JsonPrimitive) value).isNumber();
                if (!numeric) {
                    issues.add(new ValidationIssue(path + ".layout." + key, "LAYOUT_NOT_NUMERIC",
                            ValidationIssue.Severity.WARNING, "flex '" + key + "' should be a number."));
                }
            }
        }
        if (layout.has("grow")) {
            try {
                double grow = layout.get("grow").getAsDouble();
                if (grow < 0) {
                    issues.add(new ValidationIssue(path + ".layout.grow", "FLEX_NEGATIVE_GROW",
                            ValidationIssue.Severity.WARNING,
                            "flex grow=" + grow + " is negative and will behave like grow=0."));
                } else if (grow == 0 && (!layout.has("basis") || layout.get("basis").isJsonNull()
                        || layout.get("basis").getAsString().isBlank())) {
                    issues.add(new ValidationIssue(path + ".layout", "FLEX_ZERO_GROW_NO_BASIS",
                            ValidationIssue.Severity.WARNING,
                            "grow=0 with no basis may collapse the component inside a flex container."));
                }
            } catch (NumberFormatException | UnsupportedOperationException ignored) {
                // already reported as LAYOUT_NOT_NUMERIC
            }
        }
    }

    private void checkStylePlacement(JsonObject node, String path, List<ValidationIssue> issues) {
        if (node.has("props") && node.get("props").isJsonObject()) {
            JsonObject props = node.getAsJsonObject("props");
            if (props.has("style") && props.get("style").isJsonObject()) {
                JsonObject style = props.getAsJsonObject("style");
                List<String> bad = ComponentCatalog.FLEX_STYLE_KEYS.stream()
                        .filter(style::has).sorted().toList();
                if (!bad.isEmpty()) {
                    issues.add(new ValidationIssue(path + ".props.style", "STYLE_LAYOUT_KEYS",
                            ValidationIssue.Severity.WARNING,
                            "Move " + bad + " out of props.style; Perspective flex uses "
                                    + "props.direction/justify/alignItems/wrap."));
                }
            }
        }
    }

    private void checkBindings(JsonObject node, String path, List<ValidationIssue> issues, Stats stats) {
        if (!node.has("props") || !node.get("props").isJsonObject()) {
            return;
        }
        JsonObject props = node.getAsJsonObject("props");
        for (Map.Entry<String, JsonElement> entry : props.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject candidate = entry.getValue().getAsJsonObject();
            if (!candidate.has("type") || !candidate.get("type").isJsonPrimitive()) {
                continue;
            }
            String bindingType = candidate.get("type").getAsString();
            if (!ComponentCatalog.BINDING_TYPES.contains(bindingType)) {
                continue;
            }
            long foreignKeys = candidate.keySet().stream()
                    .filter(k -> !"type".equals(k) && !"config".equals(k) && !"transforms".equals(k))
                    .count();
            if (foreignKeys > 0) {
                continue;
            }
            stats.bindingCount++;
            String bindingPath = path + ".props." + entry.getKey();

            // Structural checks (existing)
            if (!candidate.has("config") || !candidate.get("config").isJsonObject()) {
                issues.add(new ValidationIssue(bindingPath, "BINDING_MISSING_CONFIG",
                        ValidationIssue.Severity.ERROR,
                        "'" + bindingType + "' binding has no config object."));
                continue;
            }
            if (candidate.has("transforms") && !candidate.get("transforms").isJsonArray()) {
                issues.add(new ValidationIssue(bindingPath + ".transforms", "TRANSFORMS_NOT_ARRAY",
                        ValidationIssue.Severity.ERROR, "'transforms' must be an array."));
            }

            JsonObject config = candidate.getAsJsonObject("config");

            // Native binding config schema validation
            Optional<PerspectiveComponentSchemaCatalog.NativeSchema> bs =
                    schemaCatalog.findBindingSchema(bindingType);
            if (bs.isPresent()) {
                for (ValidationMessage message : bs.get().schema().validate(
                        config, bs.get().schemaElement(), bindingPath + ".config")) {
                    if (issues.size() >= MAX_ISSUES) {
                        return;
                    }
                    addSchemaIssue(message, issues);
                }
            }

            // Native transform config validation
            if (candidate.has("transforms") && candidate.get("transforms").isJsonArray()) {
                JsonArray transforms = candidate.getAsJsonArray("transforms");
                for (int i = 0; i < transforms.size(); i++) {
                    if (issues.size() >= MAX_ISSUES) {
                        return;
                    }
                    JsonElement t = transforms.get(i);
                    if (!t.isJsonObject()) {
                        issues.add(new ValidationIssue(bindingPath + ".transforms[" + i + "]",
                                "TRANSFORM_NOT_OBJECT", ValidationIssue.Severity.ERROR,
                                "Each transform must be a JSON object."));
                        continue;
                    }
                    JsonObject transform = t.getAsJsonObject();
                    if (!transform.has("type") || !transform.get("type").isJsonPrimitive()) {
                        issues.add(new ValidationIssue(bindingPath + ".transforms[" + i + "]",
                                "TRANSFORM_MISSING_TYPE", ValidationIssue.Severity.ERROR,
                                "Transform must have a 'type' string."));
                        continue;
                    }
                    String transformType = transform.get("type").getAsString();
                    if (!transform.has("config") || !transform.get("config").isJsonObject()) {
                        issues.add(new ValidationIssue(bindingPath + ".transforms[" + i + "]",
                                "TRANSFORM_MISSING_CONFIG", ValidationIssue.Severity.ERROR,
                                "'" + transformType + "' transform has no config object."));
                        continue;
                    }
                    JsonObject transformConfig = transform.getAsJsonObject("config");

                    Optional<PerspectiveComponentSchemaCatalog.NativeSchema> ts =
                            schemaCatalog.findTransformSchema(transformType);
                    if (ts.isPresent()) {
                        for (ValidationMessage message : ts.get().schema().validate(
                                transformConfig, ts.get().schemaElement(),
                                bindingPath + ".transforms[" + i + "].config")) {
                            if (issues.size() >= MAX_ISSUES) {
                                return;
                            }
                            addSchemaIssue(message, issues);
                        }
                    }
                }
            }

            // Expression parsing for expr bindings (native ELParserHarness via IgnitionExpressionValidator)
            if ("expr".equals(bindingType) && config.has("expression")
                    && config.get("expression").isJsonPrimitive()) {
                String expression = config.get("expression").getAsString();
                IgnitionExpressionValidator.ValidationResult result =
                        expressionValidator.validateExpression(expression, bindingPath, "expression");
                for (IgnitionExpressionValidator.Issue issue : result.issues()) {
                    if (issues.size() >= MAX_ISSUES) {
                        return;
                    }
                    ValidationIssue.Severity severity =
                            issue.severity() == IgnitionExpressionValidator.Severity.ERROR
                                    ? ValidationIssue.Severity.ERROR
                                    : ValidationIssue.Severity.WARNING;
                    issues.add(new ValidationIssue(bindingPath, issue.code(), severity, issue.message()));
                }
            }
        }
    }

    private void checkComponentSchema(JsonObject node, String type, String path,
                                      List<ValidationIssue> issues) {
        if (issues.size() >= MAX_ISSUES || !node.has("props") || !node.get("props").isJsonObject()) {
            return;
        }
        String lookupType = ComponentCatalog.canonicalFor(type).orElse(type);
        Optional<PerspectiveComponentSchemaCatalog.ComponentSchema> foundSchema =
                schemaCatalog.find(lookupType);
        if (foundSchema.isEmpty()) {
            return;
        }
        PerspectiveComponentSchemaCatalog.ComponentSchema componentSchema = foundSchema.get();
        JsonObject props = node.getAsJsonObject("props");

        // Binding-valued props are validated structurally by checkBindings, not against the prop
        // schema: a binding object (e.g. {type: expr, config: {...}}) is not a literal of the prop's
        // declared type (e.g. text-field.text is type:string only), so skip it here to avoid
        // false positives.
        String propsAt = path + ".props";
        for (ValidationMessage message : componentSchema.schema().validate(
                propsWithoutBindings(props), componentSchema.schemaElement(), propsAt)) {
            if (issues.size() >= MAX_ISSUES) {
                return;
            }
            addSchemaIssue(message, issues);
        }

        // Props whose schema is a URN ref (e.g. style, textStyle) are validated standalone against
        // the resolved target schema, since the native engine skips unresolvable URN refs inline.
        for (Map.Entry<String, JsonElement> entry : props.entrySet()) {
            if (issues.size() >= MAX_ISSUES) {
                return;
            }
            if (isBindingValue(entry.getValue())) {
                continue;
            }
            Optional<PerspectiveComponentSchemaCatalog.ResolvedSchema> refSchema =
                    componentSchema.refSchemaFor(entry.getKey());
            if (refSchema.isEmpty()) {
                continue;
            }
            PerspectiveComponentSchemaCatalog.ResolvedSchema resolved = refSchema.get();
            String refAt = propsAt + "." + entry.getKey();
            for (ValidationMessage message : resolved.schema().validate(
                    entry.getValue(), resolved.schemaElement(), refAt)) {
                if (issues.size() >= MAX_ISSUES) {
                    return;
                }
                addSchemaIssue(message, issues);
            }
        }
    }

    private boolean isBindingValue(JsonElement value) {
        if (!value.isJsonObject()) {
            return false;
        }
        JsonObject candidate = value.getAsJsonObject();
        if (!candidate.has("type") || !candidate.get("type").isJsonPrimitive()) {
            return false;
        }
        if (!ComponentCatalog.BINDING_TYPES.contains(candidate.get("type").getAsString())) {
            return false;
        }
        long foreignKeys = candidate.keySet().stream()
                .filter(k -> !"type".equals(k) && !"config".equals(k) && !"transforms".equals(k))
                .count();
        return foreignKeys == 0;
    }

    private JsonObject propsWithoutBindings(JsonObject props) {
        JsonObject filtered = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : props.entrySet()) {
            if (isBindingValue(entry.getValue())) {
                continue;
            }
            filtered.add(entry.getKey(), entry.getValue());
        }
        return filtered;
    }

    private void addSchemaIssue(ValidationMessage message, List<ValidationIssue> issues) {
        String path = message.getPath() == null ? "$" : message.getPath();
        // Native validation paths are emitted without the leading '$' (e.g. "props.style" for a
        // "$.props" prefix); normalize to the validator's JSON-path convention.
        if (!path.startsWith("$")) {
            path = "$" + (path.isEmpty() ? "" : "." + path);
        }
        String keyword = message.getType();
        String code = "SCHEMA" + (keyword == null || keyword.isBlank()
                ? "" : "_" + keyword.toUpperCase(Locale.ROOT));
        String msg = message.getMessage() == null
                ? "Property does not conform to the component's props schema at " + path
                : message.getMessage();
        issues.add(new ValidationIssue(path, code, ValidationIssue.Severity.ERROR, msg));
    }

    private void checkPropConfigAndEvents(JsonObject node, String path, List<ValidationIssue> issues) {
        if (node.has("propConfig")) {
            if (!node.get("propConfig").isJsonObject()) {
                issues.add(new ValidationIssue(path + ".propConfig", "PROPCONFIG_NOT_OBJECT",
                        ValidationIssue.Severity.ERROR, "'propConfig' must be a JSON object."));
            } else {
                JsonObject propConfig = node.getAsJsonObject("propConfig");
                for (Map.Entry<String, JsonElement> entry : propConfig.entrySet()) {
                    if (entry.getValue().isJsonNull() || !entry.getValue().isJsonObject()) {
                        issues.add(new ValidationIssue(path + ".propConfig." + entry.getKey(),
                                "PROPCONFIG_ENTRY_NOT_OBJECT", ValidationIssue.Severity.WARNING,
                                "propConfig entries should be objects with type/config."));
                    }
                }
            }
        }
        if (node.has("events")) {
            if (!node.get("events").isJsonObject()) {
                issues.add(new ValidationIssue(path + ".events", "EVENTS_NOT_OBJECT",
                        ValidationIssue.Severity.ERROR, "'events' must be a JSON object."));
            } else {
                JsonObject events = node.getAsJsonObject("events");
                for (Map.Entry<String, JsonElement> entry : events.entrySet()) {
                    if (entry.getValue().isJsonNull() || !entry.getValue().isJsonObject()) {
                        issues.add(new ValidationIssue(path + ".events." + entry.getKey(),
                                "EVENT_ENTRY_NOT_OBJECT", ValidationIssue.Severity.WARNING,
                                "Event entries should be objects."));
                    }
                }
            }
        }
    }

    // --- result ----------------------------------------------------------------------------------

    public static final class ValidationResult {

        private final List<ValidationIssue> errors;
        private final List<ValidationIssue> warnings;
        private final int componentCount;
        private final int maxDepth;
        private final int bindingCount;

        private ValidationResult(List<ValidationIssue> errors, List<ValidationIssue> warnings,
                                 int componentCount, int maxDepth, int bindingCount) {
            this.errors = errors;
            this.warnings = warnings;
            this.componentCount = componentCount;
            this.maxDepth = maxDepth;
            this.bindingCount = bindingCount;
        }

        static ValidationResult of(List<ValidationIssue> issues) {
            return of(issues, 0, 0, 0);
        }

        static ValidationResult of(List<ValidationIssue> issues, int componentCount,
                                   int maxDepth, int bindingCount) {
            List<ValidationIssue> errors = issues.stream()
                    .filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR).toList();
            List<ValidationIssue> warnings = issues.stream()
                    .filter(issue -> issue.severity() != ValidationIssue.Severity.ERROR).toList();
            return new ValidationResult(errors, warnings, componentCount, maxDepth, bindingCount);
        }

        public boolean valid() {
            return errors.isEmpty();
        }

        public List<ValidationIssue> errors() {
            return errors;
        }

        public List<ValidationIssue> warnings() {
            return warnings;
        }

        public int componentCount() {
            return componentCount;
        }

        public int maxDepth() {
            return maxDepth;
        }

        public int bindingCount() {
            return bindingCount;
        }

        /** Response payload matching the documented endpoint contract. */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("valid", valid());
            map.put("errors", errors.stream().map(ValidationIssue::toMap).toList());
            map.put("warnings", warnings.stream().map(ValidationIssue::toMap).toList());
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("componentCount", componentCount);
            stats.put("maxDepth", maxDepth);
            stats.put("bindingCount", bindingCount);
            map.put("stats", stats);
            return map;
        }
    }

    private static final class Stats {
        int componentCount;
        int maxDepth;
        int bindingCount;
    }
}
