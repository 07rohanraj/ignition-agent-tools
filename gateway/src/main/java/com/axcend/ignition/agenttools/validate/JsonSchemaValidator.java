package com.axcend.ignition.agenttools.validate;

import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.jsonschema.JsonSchema;
import com.inductiveautomation.ignition.common.jsonschema.JsonSchemaFactory;
import com.inductiveautomation.ignition.common.jsonschema.ValidationMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gateway-safe wrapper around Ignition's native JSON Schema validator
 * ({@link com.inductiveautomation.ignition.common.jsonschema.JsonSchema} via
 * {@link JsonSchemaFactory}).
 *
 * <p>This is the native engine Ignition uses for JSON Schema validation. It validates an arbitrary
 * JSON document against a caller-supplied JSON Schema and returns structured
 * {@link ValidationMessage}s ({@code code}, {@code path}, {@code message}). It is used whenever a
 * schema is available, e.g. a user- or tool-provided schema for a view or component document, and
 * backs {@link PerspectiveComponentSchemaCatalog}'s native Perspective component {@code props}
 * schemas. Each {@link ValidationMessage} is surfaced as an {@link ValidationIssue} in the same
 * shape the rest of the validator emits ({@code path}, {@code code}, {@code severity},
 * {@code message}), so a document can be checked by the hand-written structural rules
 * <em>and</em> by a supplied schema in one pass.</p>
 *
 * <p>Passing the {@code schemaElement} (rather than the raw document) as the validator's root
 * argument is deliberate: it makes {@code #/definitions/...} ref resolution and recursive path
 * prefixes work correctly.</p>
 */
public final class JsonSchemaValidator {

    /** One native schema violation, mapped from {@link ValidationMessage}. */
    public record SchemaViolation(String path, String code, String message) {}

    private final JsonSchemaFactory factory = new JsonSchemaFactory();

    /**
     * Validates a JSON document against a caller-supplied JSON Schema using Ignition's native
     * validator.
     *
     * @param schemaElement the JSON Schema (draft/ignition URN forms supported by the native engine)
     * @param document the document to validate
     * @return all violations as {@link SchemaViolation}s; empty when valid; never {@code null}
     * @throws IllegalArgumentException if {@code schemaElement} is null/not a valid schema
     */
    public List<SchemaViolation> validate(JsonElement schemaElement, JsonElement document) {
        if (schemaElement == null) {
            throw new IllegalArgumentException("A JSON Schema ('schema') is required for native validation.");
        }
        if (document == null) {
            return List.of();
        }
        JsonSchema schema = factory.getSchema(schemaElement);
        // Native semantics: the first JsonElement is the instance/object under test, the second is
        // the schema (value/definition) it is checked against, and the third is the path prefix.
        // Passing the schemaElement as the second argument makes the (recursive) path prefixes and
        // property descent work correctly.
        Set<ValidationMessage> messages = schema.validate(document, schemaElement, "$");
        List<SchemaViolation> violations = new ArrayList<>(messages.size());
        for (ValidationMessage message : messages) {
            String path = message.getPath() == null ? "$" : message.getPath();
            String code = message.getCode() == null ? "SCHEMA" : message.getCode();
            String msg = message.getMessage() == null
                    ? "Document does not conform to the supplied schema at " + path
                    : message.getMessage();
            violations.add(new SchemaViolation(path, code, msg));
        }
        return violations;
    }

    /** Converts violations to the {@link ValidationIssue} shape used across this validator. */
    public static List<ValidationIssue> toIssues(List<SchemaViolation> violations) {
        List<ValidationIssue> issues = new ArrayList<>(violations.size());
        for (SchemaViolation violation : violations) {
            issues.add(new ValidationIssue(violation.path(), violation.code(),
                    ValidationIssue.Severity.ERROR, violation.message()));
        }
        return issues;
    }

    /** Serializes a violation for embedding in a response payload. */
    public Map<String, Object> violationToMap(SchemaViolation violation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("path", violation.path());
        map.put("code", violation.code());
        map.put("message", violation.message());
        return map;
    }
}
