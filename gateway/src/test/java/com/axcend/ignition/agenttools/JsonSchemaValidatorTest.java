package com.axcend.ignition.agenttools;

import com.axcend.ignition.agenttools.validate.JsonSchemaValidator;
import com.axcend.ignition.agenttools.validate.JsonSchemaValidator.SchemaViolation;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSchemaValidatorTest {

    private final JsonSchemaValidator validator = new JsonSchemaValidator();

    private static JsonElement parse(String json) {
        return JsonParser.parseString(json);
    }

    @Test
    void validDocumentYieldsNoViolations() {
        JsonElement schema = parse("{ \"type\": \"object\", \"properties\": { \"name\": { \"type\": \"string\" } }, \"required\": [\"name\"] }");
        JsonElement doc = parse("{ \"name\": \"Motor\" }");

        List<SchemaViolation> violations = validator.validate(schema, doc);

        assertTrue(violations.isEmpty(), "expected no violations but got " + violations);
    }

    @Test
    void missingRequiredFieldYieldsViolation() {
        JsonElement schema = parse("{ \"type\": \"object\", \"properties\": { \"name\": { \"type\": \"string\" } }, \"required\": [\"name\"] }");
        JsonElement doc = parse("{ \"other\": 1 }");

        List<SchemaViolation> violations = validator.validate(schema, doc);

        assertFalse(violations.isEmpty(), "expected a violation for missing required field");
        assertEquals("$", violations.get(0).path());
        assertTrue(violations.get(0).message() != null && !violations.get(0).message().isBlank());
    }

    @Test
    void wrongTypeYieldsViolation() {
        JsonElement schema = parse("{ \"type\": \"object\", \"properties\": { \"count\": { \"type\": \"integer\" } }, \"required\": [\"count\"] }");
        JsonElement doc = parse("{ \"count\": \"not-an-integer\" }");

        List<SchemaViolation> violations = validator.validate(schema, doc);

        assertFalse(violations.isEmpty(), "expected a type violation");
    }

    @Test
    void nullSchemaThrows() {
        try {
            validator.validate(null, parse("{}"));
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
