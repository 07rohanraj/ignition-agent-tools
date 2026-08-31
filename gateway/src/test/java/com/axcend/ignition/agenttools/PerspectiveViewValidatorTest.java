package com.axcend.ignition.agenttools;

import com.axcend.ignition.agenttools.validate.PerspectiveViewValidator;
import com.axcend.ignition.agenttools.validate.ValidationIssue;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerspectiveViewValidatorTest {

    private final PerspectiveViewValidator validator = new PerspectiveViewValidator();

    private PerspectiveViewValidator.ValidationResult validate(String json) {
        return validator.validate(JsonParser.parseString(json));
    }

    private Set<String> codes(PerspectiveViewValidator.ValidationResult result) {
        Set<String> all = result.errors().stream().map(ValidationIssue::code).collect(Collectors.toSet());
        all.addAll(result.warnings().stream().map(ValidationIssue::code).collect(Collectors.toSet()));
        return all;
    }

    @Nested
    class ValidViews {

        @Test
        void minimalValidLabel() {
            var result = validate("""
                    {
                      "type": "ia.display.label",
                      "meta": {"name": "Title", "id": "abc123"},
                      "props": {"text": "Hello"},
                      "events": {},
                      "propConfig": {}
                    }
                    """);
            assertTrue(result.valid(), () -> "unexpected errors: " + codes(result));
            assertEquals(1, result.componentCount());
            assertEquals(0, result.maxDepth());
        }

        @Test
        void nestedFlexLayout() {
            var result = validate("""
                    {
                      "type": "ia.container.flex",
                      "meta": {"name": "Root"},
                      "props": {
                        "direction": "column",
                        "wrap": "nowrap",
                        "justify": "flex-start",
                        "alignItems": "stretch",
                        "alignContent": "stretch",
                        "style": {}
                      },
                      "children": [
                        {
                          "type": "ia.display.label",
                          "meta": {"name": "A"},
                          "layout": {"grow": 1}
                        },
                        {
                          "type": "ia.chart.powerchart",
                          "meta": {"name": "Chart"},
                          "layout": {"grow": 2, "shrink": 0, "basis": "200px"}
                        }
                      ]
                    }
                    """);
            assertTrue(result.valid(), () -> "unexpected errors: " + codes(result));
            assertEquals(3, result.componentCount());
            assertEquals(1, result.maxDepth());
            assertEquals(0, result.bindingCount());
        }

        @Test
        void wellFormedBindingCounts() {
            var result = validate("""
                    {
                      "type": "ia.display.label",
                      "meta": {"name": "L"},
                      "props": {
                        "text": {"type": "expr", "config": {"expression": "\\"hi\\""}},
                        "color": {"type": "tag", "config": {"tagPath": "[default]t"}}
                      }
                    }
                    """);
            assertTrue(result.valid(), () -> "unexpected errors: " + codes(result));
            assertEquals(2, result.bindingCount());
        }
    }

    @Nested
    class StructuralErrors {

        @Test
        void nullDocument() {
            var result = validator.validate(null);
            assertFalse(result.valid());
            assertEquals("EMPTY_DOCUMENT", result.errors().get(0).code());
        }

        @Test
        void rootNotObject() {
            var result = validator.validate(JsonParser.parseString("[1,2]"));
            assertFalse(result.valid());
            assertEquals("ROOT_NOT_OBJECT", result.errors().get(0).code());
        }

        @Test
        void missingRootKeys() {
            var result = validate("""
                    {"type": "ia.display.label"}
                    """);
            assertFalse(result.valid());
            assertTrue(codes(result).containsAll(Set.of("MISSING_REQUIRED_KEY", "MISSING_META")));
            // path checks
            assertTrue(result.errors().stream()
                    .anyMatch(issue -> "$.meta".equals(issue.path())));
        }

        @Test
        void missingComponentTypeAndName() {
            var result = validate("""
                    {
                      "meta": {"name": ""},
                      "children": [{"meta": {}}]
                    }
                    """);
            assertFalse(result.valid());
            var found = codes(result);
            assertTrue(found.contains("MISSING_ROOT_TYPE"));
            assertTrue(found.contains("MISSING_COMPONENT_TYPE"));
            assertTrue(found.contains("MISSING_COMPONENT_NAME"));
            // child path uses bracket notation
            assertTrue(result.errors().stream()
                    .anyMatch(issue -> issue.path().startsWith("$.children[0]")));
        }
    }

    @Nested
    class Warnings {

        @Test
        void deprecatedAliasIsWarningNotError() {
            var result = validate("""
                    {
                      "type": "ia.text.label",
                      "meta": {"name": "Old"},
                      "props": {}
                    }
                    """);
            assertTrue(result.valid(), "alias alone must not fail validation");
            assertEquals("DEPRECATED_ALIAS", result.warnings().get(0).code());
        }

        @Test
        void unknownIaTypeWarns() {
            var result = validate("""
                    {"type": "ia.display.not-real", "meta": {"name": "X"}, "props": {}}
                    """);
            assertTrue(result.valid());
            assertTrue(result.warnings().stream()
                    .anyMatch(issue -> "UNKNOWN_COMPONENT_TYPE".equals(issue.code())));
        }

        @Test
        void duplicateSiblingNamesWarn() {
            var result = validate("""
                    {
                      "type": "ia.container.flex",
                      "meta": {"name": "R"},
                      "props": {
                        "direction": "row", "wrap": "nowrap", "justify": "flex-start",
                        "alignItems": "stretch", "alignContent": "stretch", "style": {}
                      },
                      "children": [
                        {"type": "ia.display.label", "meta": {"name": "Same"}},
                        {"type": "ia.display.label", "meta": {"name": "Same"}}
                      ]
                    }
                    """);
            assertTrue(result.valid());
            assertEquals(1, result.warnings().stream()
                    .filter(issue -> "DUPLICATE_SIBLING_NAME".equals(issue.code())).count());
        }

        @Test
        void styleLayoutKeysWarn() {
            var result = validate("""
                    {
                      "type": "ia.container.flex",
                      "meta": {"name": "R"},
                      "props": {
                        "direction": "row", "wrap": "nowrap", "justify": "flex-start",
                        "alignItems": "stretch", "alignContent": "stretch",
                        "style": {"flexDirection": "column", "backgroundColor": "#FFF"}
                      }
                    }
                    """);
            assertTrue(result.valid());
            assertTrue(result.warnings().stream()
                    .anyMatch(issue -> "STYLE_LAYOUT_KEYS".equals(issue.code())));
        }

        @Test
        void zeroGrowNoBasisWarns() {
            var result = validate("""
                    {
                      "type": "ia.container.flex",
                      "meta": {"name": "R"},
                      "props": {
                        "direction": "row", "wrap": "nowrap", "justify": "flex-start",
                        "alignItems": "stretch", "alignContent": "stretch", "style": {}
                      },
                      "children": [
                        {"type": "ia.display.label", "meta": {"name": "L"}, "layout": {"grow": 0}}
                      ]
                    }
                    """);
            assertTrue(result.valid());
            assertTrue(result.warnings().stream()
                    .anyMatch(issue -> "FLEX_ZERO_GROW_NO_BASIS".equals(issue.code())));
        }

        @Test
        void missingMetaIdDoesNotWarn() {
            // Real Ignition view.json files never persist meta.id - Designer assigns it at runtime.
            var result = validate("""
                    {"type": "ia.display.label", "meta": {"name": "NoId"}, "props": {}}
                    """);
            assertTrue(result.valid());
            assertTrue(result.warnings().isEmpty(), () -> "unexpected warnings: " + codes(result));
        }
    }

    @Nested
    class Bindings {

        @Test
        void bindingMissingConfigFails() {
            var result = validate("""
                    {
                      "type": "ia.display.label",
                      "meta": {"name": "L"},
                      "props": {"text": {"type": "expr"}}
                    }
                    """);
            assertFalse(result.valid());
            assertTrue(result.errors().stream()
                    .anyMatch(issue -> "BINDING_MISSING_CONFIG".equals(issue.code())
                            && "$.props.text".equals(issue.path())));
        }

        @Test
        void transformsMustBeArray() {
            var result = validate("""
                    {
                      "type": "ia.display.label",
                      "meta": {"name": "L"},
                      "props": {"text": {"type": "query", "config": {"queryPath": "q"}, "transforms": {}}}
                    }
                    """);
            assertFalse(result.valid());
            assertTrue(result.errors().stream()
                    .anyMatch(issue -> "TRANSFORMS_NOT_ARRAY".equals(issue.code())));
        }

        @Test
        void plainPropsWithMatchingKeysAreNotBindings() {
            var result = validate("""
                    {
                      "type": "ia.input.dropdown",
                      "meta": {"name": "D"},
                      "props": {
                        "value": {"type": "literal", "config": {}},
                        "options": [],
                        "style": {}
                      }
                    }
                    """);
            assertTrue(result.valid());
            assertEquals(0, result.bindingCount());
        }
    }

    @Nested
    class SchemaValidation {

        @Test
        void flexWithMissingRequiredPropsFails() {
            var result = validate("""
                    {
                      "type": "ia.container.flex",
                      "meta": {"name": "R"},
                      "props": {"style": {}}
                    }
                    """);
            assertFalse(result.valid());
            assertTrue(result.errors().stream()
                    .anyMatch(issue -> "SCHEMA_REQUIRED".equals(issue.code())
                            && issue.path().startsWith("$.props")));
        }

        @Test
        void flexWithUnknownPropAndBadEnumFails() {
            var result = validate("""
                    {
                      "type": "ia.container.flex",
                      "meta": {"name": "R"},
                      "props": {
                        "direction": "sideways", "wrap": "nowrap", "justify": "flex-start",
                        "alignItems": "stretch", "alignContent": "stretch", "style": {},
                        "notARealProp": true
                      }
                    }
                    """);
            assertFalse(result.valid());
            var found = result.errors().stream().map(ValidationIssue::code).collect(Collectors.toSet());
            assertTrue(found.contains("SCHEMA_ENUM"), () -> "expected enum violation in " + found);
            assertTrue(found.contains("SCHEMA_ADDITIONALPROPERTIES"),
                    () -> "expected additionalProperties violation in " + found);
        }

        @Test
        void styleUrnRefPropIsValidatedStandalone() {
            var result = validate("""
                    {
                      "type": "ia.container.flex",
                      "meta": {"name": "R"},
                      "props": {
                        "direction": "row", "wrap": "nowrap", "justify": "flex-start",
                        "alignItems": "stretch", "alignContent": "stretch",
                        "style": {"marginTop": "tall"}
                      }
                    }
                    """);
            assertFalse(result.valid());
            assertTrue(result.errors().stream().anyMatch(issue ->
                            issue.path().contains("props.style") && "SCHEMA_TYPE".equals(issue.code())),
                    () -> "expected SCHEMA_TYPE inside props.style, got " + result.errors());
        }

        @Test
        void bindingPropsAreExemptFromSchemaTypes() {
            var result = validate("""
                    {
                      "type": "ia.input.text-field",
                      "meta": {"name": "F"},
                      "props": {"text": {"type": "expr", "config": {"expression": "\\"hi\\""}}}
                    }
                    """);
            assertTrue(result.valid(), () -> "unexpected errors: " + codes(result));
            assertEquals(1, result.bindingCount());
        }

        @Test
        void literalPropWrongTypeFails() {
            var result = validate("""
                    {
                      "type": "ia.input.text-field",
                      "meta": {"name": "F"},
                      "props": {"text": 123}
                    }
                    """);
            assertFalse(result.valid());
            assertTrue(result.errors().stream()
                    .anyMatch(issue -> "SCHEMA_TYPE".equals(issue.code())
                            && issue.path().endsWith("props.text")));
        }

        @Test
        void aliasResolvesToCanonicalSchema() {
            var result = validate("""
                    {
                      "type": "ia.text.label",
                      "meta": {"name": "Old"},
                      "props": {"alignVertical": "sideways"}
                    }
                    """);
            assertFalse(result.valid(), "alias must be validated against ia.display.label's schema");
            assertTrue(result.errors().stream()
                    .anyMatch(issue -> "SCHEMA_ENUM".equals(issue.code())));
        }
    }

    @Nested
    class ViewResourceFormat {

        @Test
        void fullViewResourceValidatesRootComponent() {
            var result = validate("""
                    {
                      "custom": {},
                      "params": {"title": "Hello"},
                      "events": {},
                      "propConfig": {},
                      "root": {
                        "type": "ia.container.coord",
                        "meta": {"name": "root"},
                        "props": {"mode": "fixed", "aspectRatio": "", "style": {}},
                        "children": [
                          {"type": "ia.display.label", "meta": {"name": "L"}, "props": {}}
                        ]
                      }
                    }
                    """);
            assertTrue(result.valid(), () -> "unexpected errors: " + codes(result));
            assertEquals(2, result.componentCount());
            assertEquals(1, result.maxDepth());
        }

        @Test
        void viewKeysMustBeObjects() {
            var result = validate("""
                    {
                      "params": ["oops"],
                      "root": {
                        "type": "ia.display.label",
                        "meta": {"name": "root"},
                        "props": {}
                      }
                    }
                    """);
            assertFalse(result.valid());
            assertTrue(result.errors().stream()
                    .anyMatch(issue -> "VIEW_KEY_NOT_OBJECT".equals(issue.code())
                            && "$.params".equals(issue.path())));
        }

        @Test
        void missingRootTypeInViewWrapper() {
            var result = validate("""
                    {
                      "root": {"meta": {"name": "root"}, "props": {}}
                    }
                    """);
            assertFalse(result.valid());
            assertTrue(codes(result).contains("MISSING_ROOT_TYPE"));
            assertTrue(result.errors().stream()
                    .anyMatch(issue -> "$.root.type".equals(issue.path())));
        }
    }

    @Nested
    class ShapeChecks {

        @Test
        void childrenNotArrayFails() {
            var result = validate("""
                    {
                      "type": "ia.container.flex",
                      "meta": {"name": "R"},
                      "children": {"oops": true}
                    }
                    """);
            assertFalse(result.valid());
            assertTrue(result.errors().stream()
                    .anyMatch(issue -> "CHILDREN_NOT_ARRAY".equals(issue.code())));
        }

        @Test
        void propConfigAndEventsMustBeObjects() {
            var result = validate("""
                    {
                      "type": "ia.display.label",
                      "meta": {"name": "L"},
                      "propConfig": [],
                      "events": "nope"
                    }
                    """);
            assertFalse(result.valid());
            assertTrue(codes(result).containsAll(Set.of("PROPCONFIG_NOT_OBJECT", "EVENTS_NOT_OBJECT")));
        }

        @Test
        void layoutMustBeObjectWhenPresent() {
            var result = validate("""
                    {
                      "type": "ia.display.label",
                      "meta": {"name": "L"},
                      "layout": "fill"
                    }
                    """);
            assertFalse(result.valid());
            assertTrue(result.errors().stream()
                    .anyMatch(issue -> "LAYOUT_NOT_OBJECT".equals(issue.code())));
        }

        @Test
        void childNotObjectFails() {
            var result = validate("""
                    {
                      "type": "ia.container.flex",
                      "meta": {"name": "R"},
                      "props": {
                        "direction": "row", "wrap": "nowrap", "justify": "flex-start",
                        "alignItems": "stretch", "alignContent": "stretch", "style": {}
                      },
                      "children": ["not-a-component"]
                    }
                    """);
            assertFalse(result.valid());
            assertTrue(result.errors().stream()
                    .anyMatch(issue -> "CHILD_NOT_OBJECT".equals(issue.code())
                            && "$.children[0]".equals(issue.path())));
        }

        @Test
        void metaNotObjectFails() {
            var result = validate("""
                    {
                      "type": "ia.display.label",
                      "meta": "oops",
                      "props": {}
                    }
                    """);
            assertFalse(result.valid());
            assertTrue(result.errors().stream()
                    .anyMatch(issue -> "META_NOT_OBJECT".equals(issue.code())));
        }

        @Test
        void suspiciousTypeFormatWarns() {
            var result = validate("""
                    {"type": "label", "meta": {"name": "X"}, "props": {}}
                    """);
            assertTrue(result.valid());
            assertTrue(result.warnings().stream()
                    .anyMatch(issue -> "SUSPICIOUS_TYPE_FORMAT".equals(issue.code())));
        }

        @Test
        void negativeGrowAndNonNumericGrowWarn() {
            var result = validate("""
                    {
                      "type": "ia.container.flex",
                      "meta": {"name": "R"},
                      "props": {
                        "direction": "row", "wrap": "nowrap", "justify": "flex-start",
                        "alignItems": "stretch", "alignContent": "stretch", "style": {}
                      },
                      "children": [
                        {"type": "ia.display.label", "meta": {"name": "A"}, "layout": {"grow": -1}},
                        {"type": "ia.display.label", "meta": {"name": "B"}, "layout": {"grow": "lots"}}
                      ]
                    }
                    """);
            assertTrue(result.valid());
            assertTrue(result.warnings().stream()
                    .anyMatch(issue -> "FLEX_NEGATIVE_GROW".equals(issue.code())));
            assertTrue(result.warnings().stream()
                    .anyMatch(issue -> "LAYOUT_NOT_NUMERIC".equals(issue.code())
                            && issue.path().endsWith("layout.grow")));
        }

        @Test
        void propConfigAndEventEntriesShouldBeObjects() {
            var result = validate("""
                    {
                      "type": "ia.display.label",
                      "meta": {"name": "L"},
                      "props": {},
                      "propConfig": {"props.text": null},
                      "events": {"dom": "nope"}
                    }
                    """);
            assertTrue(result.valid());
            assertTrue(codes(result).containsAll(Set.of(
                    "PROPCONFIG_ENTRY_NOT_OBJECT", "EVENT_ENTRY_NOT_OBJECT")));
        }

        @Test
        void transformsArrayIsValidAndCountsBinding() {
            var result = validate("""
                    {
                      "type": "ia.display.label",
                      "meta": {"name": "L"},
                      "props": {"text": {"type": "query",
                                          "config": {"queryPath": "q"},
                                          "transforms": [{"code": "return value"}]}}
                    }
                    """);
            assertTrue(result.valid(), () -> "unexpected errors: " + codes(result));
            assertEquals(1, result.bindingCount());
        }

        @Test
        void udtParameterBindingRecognized() {
            var result = validate("""
                    {
                      "type": "ia.display.label",
                      "meta": {"name": "L"},
                      "props": {"text": {"type": "udtParameter", "config": {}}}
                    }
                    """);
            assertTrue(result.valid());
            assertEquals(1, result.bindingCount());
        }

        @Test
        void plainPropWithTypeAndConfigIsNotABinding() {
            var result = validate("""
                    {
                      "type": "ia.input.dropdown",
                      "meta": {"name": "D"},
                      "props": {
                        "value": {"type": "basic", "config": {}},
                        "options": [],
                        "style": {}
                      }
                    }
                    """);
            assertTrue(result.valid());
            assertEquals(0, result.bindingCount());
        }

        @Test
        void issueCapStopsRunawayValidation() {
            StringBuilder children = new StringBuilder();
            for (int i = 0; i < 150; i++) {
                if (i > 0) {
                    children.append(',');
                }
                // two issues per child: no type + blank name
                children.append("{\"meta\":{\"name\":\"\"}}");
            }
            String json = "{\"type\": \"ia.container.flex\", \"meta\": {\"name\": \"R\"}, "
                    + "\"props\": {\"direction\": \"row\", \"wrap\": \"nowrap\", \"justify\": \"flex-start\", "
                    + "\"alignItems\": \"stretch\", \"alignContent\": \"stretch\", \"style\": {}}, "
                    + "\"children\": [" + children + "]}";
            var result = validate(json);
            assertFalse(result.valid());
            assertTrue(result.warnings().stream()
                    .anyMatch(issue -> "TOO_MANY_ISSUES".equals(issue.code())),
                    () -> "expected cap warning, got " + result.errors().size() + " errors");
            assertTrue(result.errors().size() <= 201);
        }

        @Test
        void wrapperRootNotObjectDoesNotCrash() {
            var result = validate("{\"root\": \"weird\"}");
            assertFalse(result.valid()); // falls through to bare-component checks
            assertTrue(codes(result).contains("MISSING_REQUIRED_KEY"));
        }

        @Test
        void statsTrackDepth() throws Exception {
            String flexProps = "\"props\":{\"direction\":\"row\",\"wrap\":\"nowrap\","
                    + "\"justify\":\"flex-start\",\"alignItems\":\"stretch\","
                    + "\"alignContent\":\"stretch\",\"style\":{}}";
            String json = "{\"type\":\"ia.container.flex\",\"meta\":{\"name\":\"L0\"}," + flexProps
                    + ", \"children\":["
                    + "{\"type\":\"ia.container.flex\",\"meta\":{\"name\":\"L1\"}," + flexProps
                    + ", \"children\":["
                    + "{\"type\":\"ia.container.flex\",\"meta\":{\"name\":\"L2\"}," + flexProps
                    + ", \"children\":["
                    + "{\"type\":\"ia.display.label\",\"meta\":{\"name\":\"L3\"},\"props\":{}}"
                    + "]}]}]}";
            var result = validator.validate(JsonParser.parseString(json));
            assertTrue(result.valid());
            assertEquals(4, result.componentCount());
            assertEquals(3, result.maxDepth());
        }
    }
}
