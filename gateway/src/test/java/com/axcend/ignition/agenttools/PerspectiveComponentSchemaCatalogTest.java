package com.axcend.ignition.agenttools;

import com.axcend.ignition.agenttools.validate.PerspectiveComponentSchemaCatalog;
import com.axcend.ignition.agenttools.validate.PerspectiveComponentSchemaCatalog.ComponentSchema;
import com.axcend.ignition.agenttools.validate.PerspectiveComponentSchemaCatalog.ResolvedSchema;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import com.inductiveautomation.ignition.common.jsonschema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerspectiveComponentSchemaCatalogTest {

    private final PerspectiveComponentSchemaCatalog catalog =
            PerspectiveComponentSchemaCatalog.getInstance();

    @Test
    void loadsNativeComponentSchemasFromClasspath() {
        assertFalse(catalog.isEmpty(), "perspective-common descriptor files must be on the test classpath");
        // one id from each of the descriptor files the catalog reads
        assertTrue(catalog.find("ia.container.flex").isPresent());
        assertTrue(catalog.find("ia.display.barcode").isPresent());
        assertTrue(catalog.find("ia.chart.timeseries").isPresent());
        assertTrue(catalog.find("ia.display.google-map").isPresent());
        assertTrue(catalog.find("ia.chart.gauge").isPresent());
        assertTrue(catalog.find("ia.display.pdf-viewer").isPresent());
    }

    @Test
    void flexSchemaElementIsTheNativeDescriptorSchema() {
        ComponentSchema flex = catalog.find("ia.container.flex").orElseThrow();
        assertNotNull(flex.schemaElement());
        assertNotNull(flex.schema());
        // structurals mirror the actual ia.components.json entry
        assertTrue(flex.schemaElement().getAsJsonObject().has("required"));
    }

    @Test
    void unknownAndNullLookupsAreEmpty() {
        assertTrue(catalog.find("no.such.component").isEmpty());
        assertTrue(catalog.find(null).isEmpty());
    }

    @Test
    void missingRequiredKeysFailAgainstNativeSchema() {
        ComponentSchema flex = catalog.find("ia.container.flex").orElseThrow();
        JsonElement bareProps = JsonParser.parseString("{}");
        Set<ValidationMessage> messages =
                flex.schema().validate(bareProps, flex.schemaElement(), "$.props");
        assertFalse(messages.isEmpty(), "flex requires direction/wrap/justify/alignItems/alignContent/style");
        assertTrue(messages.stream()
                .anyMatch(m -> "required".equals(m.getType())));
    }

    @Test
    void enumViolationReported() {
        ComponentSchema flex = catalog.find("ia.container.flex").orElseThrow();
        JsonElement props = JsonParser.parseString("""
                {"direction": "sideways", "wrap": "nowrap", "justify": "flex-start",
                 "alignItems": "stretch", "alignContent": "stretch", "style": {}}
                """);
        Set<ValidationMessage> messages = flex.schema().validate(props, flex.schemaElement(), "$.props");
        assertTrue(messages.stream().anyMatch(m -> "enum".equals(m.getType())),
                () -> "expected enum violation but got " + messages);
    }

    @Test
    void urnRefPropsResolveStandalone() {
        ComponentSchema coord = catalog.find("ia.container.coord").orElseThrow();
        Optional<ResolvedSchema> styleSchema = coord.refSchemaFor("style");
        assertTrue(styleSchema.isPresent(), "coord.style is a urn:ignition-schema ref");
        assertTrue(styleSchema.get().schemaElement().getAsJsonObject().has("properties"));

        // a valid style property type passes
        JsonElement good = JsonParser.parseString("{\"marginTop\": 4}");
        assertTrue(styleSchema.get().schema().validate(good, styleSchema.get().schemaElement(), "$.props.style").isEmpty());

        // a wrong-typed style value fails
        JsonElement bad = JsonParser.parseString("{\"marginTop\": \"tall\"}");
        assertFalse(styleSchema.get().schema().validate(bad, styleSchema.get().schemaElement(), "$.props.style").isEmpty());
    }

    @Test
    void sizeReflectsAllLoadedComponents() {
        assertTrue(catalog.size() > 70, "ia.components.json alone defines ~70 components");
    }
}