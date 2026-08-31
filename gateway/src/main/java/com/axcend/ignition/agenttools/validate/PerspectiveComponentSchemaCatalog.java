package com.axcend.ignition.agenttools.validate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import com.inductiveautomation.ignition.common.jsonschema.JsonSchema;
import com.inductiveautomation.ignition.common.jsonschema.JsonSchemaFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native Data: the Perspective component descriptor files ({@code ia.components.json},
 * {@code barcode.component.json}, {@code perspective-timeseries.components.json}, ...) bundled in
 * {@code perspective-common}. Each descriptor carries the JSON Schema for that component's
 * {@code props} object (the exact schema the Designer uses to validate props).
 *
 * <p>This catalog also loads the native <b>binding config schemas</b> ({@code schemas/binding-expr.json},
 * {@code schemas/binding-tag.json}, etc.) and <b>transform schemas</b> ({@code schemas/transform-expr.json},
 * {@code schemas/transform-format.json}, {@code schemas/transform-map.json}) shipped in the same
 * {@code perspective-common} jar. These are the exact schemas the Designer validates binding/transform
 * configurations against. The binding type discriminator (e.g. {@code "expr"}, {@code "tag"}) maps
 * to the corresponding schema, and each transform type maps similarly.</p>
 *
 * <p>The only custom layer is resolving the {@code urn:ignition-schema:...} {@code $ref}s used on
 * props like {@code style}: native {@link JsonSchema#validate} silently skips a URN {@code $ref}
 * unless the owning schema has a subSchema pre-wired (a Gateway-side {@code JsonSchema} built by
 * the factory never does). To keep those props checkable, each URN target (e.g.
 * {@code schemas/style-properties.schema.json}) is loaded from the classpath and compiled into its
 * own standalone native {@link JsonSchema}, which still resolves its internal
 * {@code #/definitions/...} refs natively.</p>
 *
 * <p>Deliberately resilient: if the descriptor resources are unavailable (e.g. a gateway install
 * without the Perspective module), the catalog is empty and callers fall back to structural checks
 * only. Never throws during construction.</p>
 */
public final class PerspectiveComponentSchemaCatalog {

    private static final Logger logger =
            LoggerFactory.getLogger(PerspectiveComponentSchemaCatalog.class);

    private static final String URN_PREFIX = "urn:ignition-schema:";

    /** The descriptor files {@code ComponentRegistry} itself reads, in the same root location. */
    private static final List<String> DEF_FILES = List.of(
            "ia.components.json",
            "barcode.component.json",
            "perspective-timeseries.components.json",
            "perspective-googlemap.components.json",
            "perspective-amcharts.components.json",
            "perspective-map.components.json",
            "pdf-viewer.components.json"
    );

    private static final PerspectiveComponentSchemaCatalog INSTANCE =
            new PerspectiveComponentSchemaCatalog();

    /** A standalone native schema compiled from a resolved URN target. */
    public record ResolvedSchema(JsonElement schemaElement, JsonSchema schema) {}

    /** A general-purpose native schema (binding config, transform config, etc.). */
    public record NativeSchema(JsonElement schemaElement, JsonSchema schema) {}

    /** One component's native props schema plus any URN-{@code $ref} props resolved standalone. */
    public record ComponentSchema(String typeId, JsonElement schemaElement, JsonSchema schema,
                                  Map<String, ResolvedSchema> refPropSchemas) {

        public Optional<ResolvedSchema> refSchemaFor(String propName) {
            return Optional.ofNullable(refPropSchemas.get(propName));
        }
    }

    /** Binding type discriminator → native config schema file (from {@code perspective-common}). */
    private static final Map<String, String> BINDING_SCHEMA_FILES = Map.of(
            "expr",         "schemas/binding-expr.json",
            "tag",          "schemas/binding-tag.json",
            "query",        "schemas/binding-query.json",
            "property",     "schemas/binding-property.json",
            "tag-history",  "schemas/binding-tag-history.json",
            "http",         "schemas/binding-http.json"
    );

    /** Transform type discriminator → native config schema file. */
    private static final Map<String, String> TRANSFORM_SCHEMA_FILES = Map.of(
            "expr",   "schemas/transform-expr.json",
            "format", "schemas/transform-format.json",
            "map",    "schemas/transform-map.json"
    );

    private final Map<String, ComponentSchema> byTypeId = new LinkedHashMap<>();
    private final JsonSchemaFactory factory = new JsonSchemaFactory();
    private final Map<String, ResolvedSchema> resolvedUrnSchemas = new HashMap<>();
    private final Map<String, NativeSchema> bindingSchemas = new LinkedHashMap<>();
    private final Map<String, NativeSchema> transformSchemas = new LinkedHashMap<>();

    public static PerspectiveComponentSchemaCatalog getInstance() {
        return INSTANCE;
    }

    private PerspectiveComponentSchemaCatalog() {
        loadDescriptorFiles();
        loadBindingSchemas();
        loadTransformSchemas();
    }

    /** @return the native props schema for a component id, never {@code null} */
    public Optional<ComponentSchema> find(String typeId) {
        return typeId == null ? Optional.empty() : Optional.ofNullable(byTypeId.get(typeId));
    }

    public int size() {
        return byTypeId.size();
    }

    public boolean isEmpty() {
        return byTypeId.isEmpty();
    }

    private void loadDescriptorFiles() {
        int loaded = 0;
        for (String defFile : DEF_FILES) {
            try {
                String content = readResource(defFile);
                if (content == null) {
                    continue;
                }
                JsonElement parsed = JsonParser.parseString(content);
                if (!parsed.isJsonObject()) {
                    continue;
                }
                addComponents(parsed.getAsJsonObject());
                loaded++;
            } catch (Exception exception) {
                logger.warn("Skipping component descriptor '{}': {}", defFile, exception.toString());
            }
        }
        logger.debug("PerspectiveComponentSchemaCatalog loaded {} descriptor files: {} components",
                loaded, byTypeId.size());
    }

    /** Loads native binding config schemas from {@code perspective-common} classpath resources. */
    private void loadBindingSchemas() {
        for (Map.Entry<String, String> entry : BINDING_SCHEMA_FILES.entrySet()) {
            String typeId = entry.getKey();
            String file = entry.getValue();
            try {
                String content = readResource(file);
                if (content == null) {
                    logger.warn("Could not find binding schema file '{}' on classpath", file);
                    continue;
                }
                JsonElement element = JsonParser.parseString(content);
                if (!element.isJsonObject()) {
                    logger.warn("Binding schema file '{}' is not a JSON object", file);
                    continue;
                }
                JsonSchema schema = factory.getSchema(element);
                bindingSchemas.put(typeId, new NativeSchema(element, schema));
            } catch (Exception e) {
                logger.warn("Could not load binding schema for '{}': {}", typeId, e.toString());
            }
        }
    }

    /** Loads native transform config schemas from {@code perspective-common} classpath resources.
     * For transform-map.json, URN $ref nodes inside definitions are resolved in-place so that
     * style output validation can proceed via native #/definitions resolution. */
    private void loadTransformSchemas() {
        for (Map.Entry<String, String> entry : TRANSFORM_SCHEMA_FILES.entrySet()) {
            String typeId = entry.getKey();
            String file = entry.getValue();
            try {
                String content = readResource(file);
                if (content == null) {
                    logger.warn("Could not find transform schema file '{}' on classpath", file);
                    continue;
                }
                JsonElement element = JsonParser.parseString(content);
                if (!element.isJsonObject()) {
                    logger.warn("Transform schema file '{}' is not a JSON object", file);
                    continue;
                }
                JsonObject schemaElement = element.getAsJsonObject();
                // Resolve URN $ref in definitions (needed for transform-map.json style-$ref)
                JsonObject resolvedElement = resolveUrnRefsInDefinitions(schemaElement);
                JsonSchema schema = factory.getSchema(resolvedElement);
                transformSchemas.put(typeId, new NativeSchema(resolvedElement, schema));
            } catch (Exception e) {
                logger.warn("Could not load transform schema for '{}': {}", typeId, e.toString());
            }
        }
    }

    /** Resolves URN {@code $ref} nodes inside a schema's {@code definitions} section.
     * Transform-map.json stores a URN $ref under {@code definitions.style} pointing to
     * {@code urn:ignition-schema:schemas/style-properties.schema.json}. This method replaces
     * that node with the resolved content so that subsequent {@code #/definitions/style}
     * references are satisfied natively. */
    private JsonObject resolveUrnRefsInDefinitions(JsonObject schemaElement) {
        if (!schemaElement.has("definitions") || !schemaElement.get("definitions").isJsonObject()) {
            return schemaElement;
        }
        JsonObject defs = schemaElement.getAsJsonObject("definitions");
        JsonObject newDefs = new JsonObject();
        boolean changed = false;
        for (Map.Entry<String, JsonElement> entry : defs.entrySet()) {
            JsonElement defValue = entry.getValue();
            if (defValue.isJsonObject()) {
                String urn = urnRefOf(defValue.getAsJsonObject());
                if (urn != null) {
                    ResolvedSchema resolved = resolveUrn(urn);
                    if (resolved != null) {
                        newDefs.add(entry.getKey(), resolved.schemaElement());
                        changed = true;
                        continue;
                    }
                }
            }
            newDefs.add(entry.getKey(), defValue);
        }
        if (!changed) {
            return schemaElement;
        }
        JsonObject result = schemaElement.deepCopy();
        result.add("definitions", newDefs);
        return result;
    }

    /** @return the native config schema for a binding type, never {@code null} */
    public Optional<NativeSchema> findBindingSchema(String bindingType) {
        return bindingType == null ? Optional.empty() : Optional.ofNullable(bindingSchemas.get(bindingType));
    }

    /** @return the native config schema for a transform type, never {@code null} */
    public Optional<NativeSchema> findTransformSchema(String transformType) {
        return transformType == null ? Optional.empty() : Optional.ofNullable(transformSchemas.get(transformType));
    }

    /** @return the native meta schema, or {@code empty} if unavailable */
    public Optional<NativeSchema> findMetaSchema() {
        try {
            String content = readResource("schemas/meta-schema.json");
            if (content == null) {
                return Optional.empty();
            }
            JsonElement element = JsonParser.parseString(content);
            if (!element.isJsonObject()) {
                return Optional.empty();
            }
            JsonSchema schema = factory.getSchema(element);
            return Optional.of(new NativeSchema(element, schema));
        } catch (Exception e) {
            logger.warn("Could not load meta schema: {}", e.toString());
            return Optional.empty();
        }
    }

    private void addComponents(JsonObject def) {
        if (!def.has("components") || !def.get("components").isJsonArray()) {
            return;
        }
        for (JsonElement element : def.getAsJsonArray("components")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject component = element.getAsJsonObject();
            String typeId = component.has("id") && component.get("id").isJsonPrimitive()
                    ? component.get("id").getAsString() : null;
            if (typeId == null || typeId.isBlank() || !component.has("schema")
                    || !component.get("schema").isJsonObject()) {
                continue;
            }
            JsonObject schemaElement = component.getAsJsonObject("schema");
            try {
                JsonSchema schema = factory.getSchema(schemaElement);
                byTypeId.put(typeId,
                        new ComponentSchema(typeId, schemaElement, schema, resolveRefProps(schemaElement)));
            } catch (Exception exception) {
                logger.warn("Could not compile schema for component '{}': {}", typeId, exception.toString());
            }
        }
    }

    /**
     * Discovers {@code properties.*} props whose schema is a pure {@code urn:ignition-schema:} ref
     * and compiles each resolved URN target into its own native schema. Native {@code validate()}
     * skips such props (parent schema has no subSchema), so they are validated separately.
     */
    private Map<String, ResolvedSchema> resolveRefProps(JsonObject schemaElement) {
        Map<String, ResolvedSchema> refProps = new LinkedHashMap<>();
        if (!schemaElement.has("properties") || !schemaElement.get("properties").isJsonObject()) {
            return refProps;
        }
        JsonObject properties = schemaElement.getAsJsonObject("properties");
        for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
            JsonElement propSchema = entry.getValue();
            if (!propSchema.isJsonObject()) {
                continue;
            }
            String ref = urnRefOf(propSchema.getAsJsonObject());
            if (ref == null) {
                continue;
            }
            ResolvedSchema resolved = resolveUrn(ref);
            if (resolved != null) {
                refProps.put(entry.getKey(), resolved);
            }
        }
        return refProps;
    }

    /** Returns the {@code urn:ignition-schema:} ref value of {@code node}, or {@code null}. */
    private static String urnRefOf(JsonObject node) {
        if (!node.has("$ref") || !node.get("$ref").isJsonPrimitive()) {
            return null;
        }
        String ref = node.get("$ref").getAsString();
        return ref.startsWith(URN_PREFIX) ? ref : null;
    }

    /**
     * Loads a URN target (e.g. {@code urn:ignition-schema:schemas/style-properties.schema.json})
     * from the classpath and compiles it into a standalone native schema. Cached by resolved path.
     */
    private synchronized ResolvedSchema resolveUrn(String urn) {
        String path = urn.startsWith(URN_PREFIX) ? urn.substring(URN_PREFIX.length()) : urn;
        ResolvedSchema cached = resolvedUrnSchemas.get(path);
        if (cached != null) {
            return cached;
        }
        try {
            String content = readResource(path);
            if (content == null) {
                logger.warn("Could not resolve schema ref '{}': resource not on classpath", urn);
                return null;
            }
            JsonElement element = JsonParser.parseString(content);
            JsonSchema schema = factory.getSchema(element);
            ResolvedSchema resolved = new ResolvedSchema(element, schema);
            resolvedUrnSchemas.put(path, resolved);
            return resolved;
        } catch (Exception exception) {
            logger.warn("Could not resolve schema ref '{}': {}", urn, exception.toString());
            return null;
        }
    }

    /** Reads a classpath resource by loader-relative or classpath-root name, or {@code null}. */
    private String readResource(String name) {
        List<String> candidates = new ArrayList<>(2);
        candidates.add(name);
        if (!name.startsWith("/")) {
            candidates.add("/" + name);
        }
        ClassLoader loader = PerspectiveComponentSchemaCatalog.class.getClassLoader();
        for (String candidate : candidates) {
            try (InputStream stream = loader.getResourceAsStream(candidate)) {
                if (stream == null) {
                    continue;
                }
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // try next candidate
            }
        }
        return null;
    }
}