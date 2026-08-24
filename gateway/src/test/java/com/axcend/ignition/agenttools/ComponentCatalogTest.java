package com.axcend.ignition.agenttools;

import com.axcend.ignition.agenttools.validate.ComponentCatalog;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentCatalogTest {

    @Test
    void stockComponentsAreKnown() {
        assertTrue(ComponentCatalog.isKnown("ia.display.label"));
        assertTrue(ComponentCatalog.isKnown("ia.container.flex"));
        assertTrue(ComponentCatalog.isKnown("ia.chart.powerchart"));
        assertTrue(ComponentCatalog.isKnown("ia.input.button"));
    }

    @Test
    void unknownAndNullTypesAreNotKnown() {
        assertFalse(ComponentCatalog.isKnown("ia.display.not-real"));
        assertFalse(ComponentCatalog.isKnown(null));
        assertFalse(ComponentCatalog.isKnown(""));
    }

    @Test
    void aliasesMapToCanonicalIds() {
        assertEquals(Optional.of("ia.display.label"), ComponentCatalog.canonicalFor("ia.text.label"));
        assertEquals(Optional.of("ia.chart.simple-gauge"), ComponentCatalog.canonicalFor("ia.display.gauge"));
        assertEquals(Optional.of("ia.input.toggle-switch"), ComponentCatalog.canonicalFor("ia.input.toggleSwitch"));
    }

    @Test
    void canonicalIdsHaveNoAliasEntry() {
        // A canonical id must never also be an alias target of itself or look deprecated.
        for (String alias : ComponentCatalog.ALIASES.keySet()) {
            assertTrue(ComponentCatalog.ALIASES.get(alias) != null
                    && !alias.equals(ComponentCatalog.ALIASES.get(alias)),
                    () -> "self-alias detected: " + alias);
        }
    }

    @Test
    void flexStyleKeysAndBindingTypesArePopulated() {
        assertTrue(ComponentCatalog.FLEX_STYLE_KEYS.containsAll(
                java.util.Set.of("flexDirection", "justifyContent", "alignItems", "flexWrap")));
        assertTrue(ComponentCatalog.BINDING_TYPES.containsAll(java.util.Set.of(
                "expr", "tag", "query", "property", "tag-history", "http", "message")));
    }

    @Test
    void everyKnownTypeIsDottedAndPrefixed() {
        for (String type : ComponentCatalog.KNOWN_TYPES) {
            assertTrue(type.startsWith("ia.") && type.indexOf('.') < type.lastIndexOf('.'),
                    () -> "malformed catalog entry: " + type);
        }
    }
}
